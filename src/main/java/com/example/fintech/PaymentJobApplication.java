package com.example.fintech;

import java.time.Instant;

public final class PaymentJobApplication {
    private PaymentJobApplication() {}

    public static void main(String[] args) {
        ServiceConfiguration configuration = ServiceConfiguration.load();
        InfraiAuditClient client = new InfraiAuditClient(configuration);
        PaymentRiskService service = new PaymentRiskService(
                configuration.serviceName(), configuration.environment());
        PaymentRiskService.PaymentEvent payment = new PaymentRiskService.PaymentEvent(
                "pay-20260929-1042", 125_00L, "USD", "merchant-204", 82,
                "tokenized-card-88", "not-exported");
        PaymentRiskService.Decision decision = service.assess(
                payment, "resolved-by-account-whoami", Instant.parse("2026-09-29T08:30:00Z"));
        String matches = service.shipAndSearch(payment, client, Instant.now());
        System.out.println(decision.notification());
        System.out.println("Search result: " + matches);
    }
}

