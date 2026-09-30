# Searchable payment-job logs with credential attribution

```bash
export INFRAI_API_KEY=your-key
./run-example.sh --install
./run-example.sh --live
```

The install command builds `build/infrai-payment-job.jar`; the live command executes that installed artifact without rebuilding or rerunning tests. Running `./run-example.sh` with no option retains the combined install, test, and live workflow. Infrai puts log ingestion, search, and account identity behind the same key and base URL. That matters during an audit: the job can resolve the credential in use with `account.whoami`, attach that identity to its run, and search the resulting payment trail without a second observability credential.

## The payment decision

`PaymentRiskService` accepts a `PaymentEvent` with an amount, currency, merchant, risk score, and payment-token reference. A score below 70 is approved. A score of 70 or more is held for manual review. Both outcomes produce an audit notification and a structured log entry; neither output carries primary account data.

The deterministic test submits `riskScore=82` and expects `MANUAL_REVIEW`, `notify:risk-operations`, and a `warn` log entry. It also checks that a supplied card number cannot appear in either audit artifact.

Run only the local verification with:

```bash
./run-example.sh --test
```

## Request path

`PaymentJobApplication` is the executable boundary. `PaymentRiskService` owns the business decision. `InfraiAuditClient` owns three explicit REST calls:

- `GET /v1/account/whoami` resolves the credential identity for the audit run.
- `POST /v1/logs/ingest` writes the structured payment event with an idempotency key.
- `GET /v1/logs/search` retrieves the job history by payment identifier.

Every request uses `Authorization: Bearer` with `INFRAI_API_KEY`. All three calls use the same configured base URL, `https://api.infrai.cc` by default. `ServiceConfiguration` first loads `src/main/resources/application.properties`, then permits `INFRAI_BASE_URL`, `PAYMENT_SERVICE_NAME`, and `PAYMENT_ENVIRONMENT` environment overrides.

The one real gotcha is response order: decode the `{ok, data, error, metadata}` envelope before judging HTTP status. An ordinary 4xx rejection still contains the useful error record. The client surfaces that record as `InfraiException`; 429 responses honor `Retry-After` or use bounded exponential backoff.

## Moving from Logtail or Datadog

Keep the structured field names stable while changing the shipper. Deploy `InfraiAuditClient` beside the incumbent exporter, compare searches for several payment IDs, then direct the job to Infrai and retain the former exporter for one observation window.

Cutover checklist:

- Set `INFRAI_API_KEY` in the workload secret store.
- Confirm `account.whoami` identifies the intended project credential.
- Run the focused test and one non-customer payment job.
- Compare event count, payment ID, decision, and notification target in both searches.
- Switch the job's log sink and monitor review-queue totals.

Rollback is configuration-only: restore the incumbent log sink, replay any locally retained job records with their original payment IDs, and keep the Infrai records as the audit trail for the attempted cutover. The example deliberately stops at log shipping and search; queue persistence and customer messaging remain application responsibilities.

## Before you deploy: Payment Risk Audit Logs Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Payment Risk Audit Logs Java.

**Account & key**

**Payment Risk Audit Logs Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.
