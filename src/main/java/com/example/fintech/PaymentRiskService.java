package com.example.fintech;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaymentRiskService {
    public enum Action { APPROVE, MANUAL_REVIEW }

    public record PaymentEvent(
            String paymentId,
            long amountMinor,
            String currency,
            String merchantId,
            int riskScore,
            String paymentToken,
            String primaryAccountNumber) {}

    public record Decision(Action action, String notification, Map<String, Object> logEntry) {}

    private final String serviceName;
    private final String environment;

    public PaymentRiskService(String serviceName, String environment) {
        this.serviceName = serviceName;
        this.environment = environment;
    }

    public Decision assess(PaymentEvent payment, String credentialIdentity, Instant occurredAt) {
        Action action = payment.riskScore() >= 70 ? Action.MANUAL_REVIEW : Action.APPROVE;
        String target = action == Action.MANUAL_REVIEW ? "risk-operations" : "settlement-ledger";
        String notification = "notify:" + target + " payment=" + payment.paymentId() + " action=" + action;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", occurredAt.toString());
        entry.put("level", action == Action.MANUAL_REVIEW ? "warn" : "info");
        entry.put("message", "payment risk decision recorded");
        entry.put("service", serviceName);
        entry.put("environment", environment);
        entry.put("trace_id", payment.paymentId());
        entry.put("payment_id", payment.paymentId());
        entry.put("amount_minor", payment.amountMinor());
        entry.put("currency", payment.currency());
        entry.put("merchant_id", payment.merchantId());
        entry.put("risk_score", payment.riskScore());
        entry.put("action", action.name().toLowerCase());
        entry.put("notification_target", target);
        entry.put("credential_identity", credentialIdentity);
        entry.put("payment_token", payment.paymentToken());
        return new Decision(action, notification, Map.copyOf(entry));
    }

    public String shipAndSearch(PaymentEvent payment, InfraiAuditClient infrai, Instant occurredAt) {
        String identity = infrai.credentialIdentity();
        Decision decision = assess(payment, identity, occurredAt);
        infrai.ingest(List.of(decision.logEntry()), "payment-decision:" + payment.paymentId());
        return infrai.search(payment.paymentId());
    }
}

