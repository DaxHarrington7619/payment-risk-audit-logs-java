package com.example.fintech;

import java.time.Instant;

public final class PaymentRiskServiceTest {
    public static void main(String[] args) {
        PaymentRiskService service = new PaymentRiskService("settlement-risk-job", "test");
        String pan = "4111111111111111";
        PaymentRiskService.PaymentEvent payment = new PaymentRiskService.PaymentEvent(
                "pay-review-7", 240_00L, "USD", "merchant-17", 82, "token-7", pan);

        PaymentRiskService.Decision decision = service.assess(
                payment, "{\"credential\":\"key-audit-4\"}", Instant.parse("2026-09-29T08:30:00Z"));

        require(decision.action() == PaymentRiskService.Action.MANUAL_REVIEW, "high risk payment must be reviewed");
        require(decision.notification().startsWith("notify:risk-operations"), "risk team must be notified");
        require("warn".equals(decision.logEntry().get("level")), "review log must be warn level");
        require(!decision.notification().contains(pan), "notification must not contain account data");
        require(!decision.logEntry().toString().contains(pan), "log must not contain account data");
        require(decision.logEntry().get("credential_identity").toString().contains("key-audit-4"),
                "log must retain the resolved credential identity");
        System.out.println("PASS: high-risk payment is held with audit-safe output");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

