package com.example.fintech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiAuditClient {
    // Exact capabilities used here: infrai.account.whoami, infrai.logs.ingest, infrai.logs.search.
    private static final String WHOAMI_PATH = "/v1/account/whoami";
    private static final String INGEST_PATH = "/v1/logs/ingest";
    private static final String SEARCH_PATH = "/v1/logs/search";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern DATA = Pattern.compile("\\\"data\\\"\\s*:\\s*(\\{.*?\\}|\\[.*?\\]|null)(?=\\s*,\\s*\\\"(?:error|metadata)\\\"|\\s*})", Pattern.DOTALL);

    private final ServiceConfiguration configuration;
    private final HttpClient http;

    public InfraiAuditClient(ServiceConfiguration configuration) {
        this.configuration = configuration;
        this.http = HttpClient.newBuilder().connectTimeout(configuration.requestTimeout()).build();
    }

    public String credentialIdentity() {
        return request("GET", WHOAMI_PATH, null, null);
    }

    public String ingest(List<Map<String, Object>> entries, String idempotencyKey) {
        String body = "{\"entries\":" + jsonValue(entries) + ",\"idempotency_key\":" + quote(idempotencyKey) + "}";
        return request("POST", INGEST_PATH, body, idempotencyKey);
    }

    public String search(String paymentId) {
        String query = "?q=" + encode(paymentId)
                + "&service=" + encode(configuration.serviceName())
                + "&environment=" + encode(configuration.environment())
                + "&limit=25";
        return request("GET", SEARCH_PATH + query, null, null);
    }

    private String request(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < configuration.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(configuration.baseUrl().resolve(path))
                    .timeout(configuration.requestTimeout())
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt + 1 < configuration.maxAttempts()) {
                    pause(retryDelay(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.code(), envelope.message(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IOException("HTTP status " + response.statusCode());
                }
                return envelope.data();
            } catch (IOException exception) {
                if (attempt + 1 == configuration.maxAttempts()) {
                    throw new IllegalStateException("Log transport retry limit reached", exception);
                }
                pause(Duration.ofMillis(250L << attempt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", exception);
            }
        }
        throw new IllegalStateException("Retry limit reached");
    }

    private static Envelope decodeEnvelope(String json, int statusCode) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) throw new IOException("Invalid response envelope at HTTP " + statusCode);
        boolean accepted = Boolean.parseBoolean(ok.group(1));
        return new Envelope(accepted, match(DATA, json, "null"),
                match(CODE, json, "REQUEST_REJECTED"), match(MESSAGE, json, "Request rejected"));
    }

    private static String match(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value)));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }).orElse(Duration.ofMillis(250L << attempt));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry wait interrupted", exception);
        }
    }

    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(',');
                first = false;
                json.append(quote(String.valueOf(entry.getKey()))).append(':').append(jsonValue(entry.getValue()));
            }
            return json.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) json.append(',');
                first = false;
                json.append(jsonValue(item));
            }
            return json.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Envelope(boolean ok, String data, String code, String message) {}
}

