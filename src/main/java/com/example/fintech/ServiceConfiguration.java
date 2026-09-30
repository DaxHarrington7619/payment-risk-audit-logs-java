package com.example.fintech;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public record ServiceConfiguration(
        URI baseUrl,
        String apiKey,
        String serviceName,
        String environment,
        int maxAttempts,
        Duration requestTimeout) {

    public static ServiceConfiguration load() {
        Properties properties = new Properties();
        try (InputStream stream = ServiceConfiguration.class.getResourceAsStream("/application.properties")) {
            if (stream == null) {
                throw new IllegalStateException("application.properties is required");
            }
            properties.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read application properties", exception);
        }
        Map<String, String> environment = System.getenv();
        String apiKey = environment.get("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new ServiceConfiguration(
                URI.create(environment.getOrDefault("INFRAI_BASE_URL", properties.getProperty("infrai.base-url"))),
                apiKey,
                environment.getOrDefault("PAYMENT_SERVICE_NAME", properties.getProperty("payment.service-name")),
                environment.getOrDefault("PAYMENT_ENVIRONMENT", properties.getProperty("payment.environment")),
                Integer.parseInt(properties.getProperty("infrai.max-attempts")),
                Duration.ofSeconds(20));
    }
}

