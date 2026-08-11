# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarketPulse is a multi-module project for enterprise/professional-level markets data tracking and analysis. It currently hosts one module, `markets-ref-data`, a Spring Boot microservice that handles external data extraction and reference data management (fetching daily NSE Bhavcopy EOD price files, filtered to equities, and persisting them to TimescaleDB). Future modules are expected to live as sibling directories alongside `markets-ref-data`.

This module was originally a Python script under this same directory name; it was rewritten in Java/Spring Boot to run as a long-lived, continuously-deployed service rather than a script invoked by an external scheduler.

## Commands

All commands below are run from the `markets-ref-data/` directory. Requires JDK 21 and Maven.

```powershell
cd markets-ref-data

# Run the full test suite (unit tests only - see note below)
mvn test

# Run a single test class
mvn test -Dtest=BhavcopyServiceTest

# Run a single test method
mvn test -Dtest=BhavcopyServiceTest#treats404AsNotFoundRatherThanFailure

# Run the Testcontainers integration test (opt-in, needs a running Docker daemon)
mvn test -Dtest=BhavcopyServiceIT

# Run the backfill integration test (opt-in, needs a running Docker daemon)
mvn test -Dtest=BackfillServiceIT

# Run the app locally in the foreground - needs a reachable DB first
docker compose up -d timescaledb
mvn spring-boot:run

# Build a runnable jar
mvn package
java -jar target/markets-ref-data-0.1.0-SNAPSHOT.jar

# Build and run the whole stack via Docker (recommended - see "Deployment" below)
docker compose up -d --build
```

**`mvn test` does not run `BhavcopyServiceIT`.** There is no failsafe plugin configured, and Surefire's default includes (`*Test`, `*Tests`, `Test*`) don't match the `*IT` suffix, so the Testcontainers integration test only runs when named explicitly. `mvn test` runs 42 tests and needs neither Docker nor network.

**The app cannot start without a reachable TimescaleDB** — Flyway migrates on boot and JPA runs with `ddl-auto: validate`. Connection settings come from `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`, defaulting to `jdbc:postgresql://localhost:5432/marketpulse` (which the composed `timescaledb` container serves on the published port).

There is no linter/formatter config in this repo yet.

## Architecture

### Package layout (`com.marketpulse.refdata`)

1. **[config/NseProperties.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/NseProperties.java)** — `@ConfigurationProperties(prefix = "nse")`: base/archive URLs, the browser-like `headers` map, and nested `scheduler.{enabled,cron,zone}`. Backed by [application.yml](markets-ref-data/src/main/resources/application.yml) — note header keys use YAML bracket syntax (`"[User-Agent]"`) so hyphenated names bind literally.
2. **[config/HttpClientConfig.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/HttpClientConfig.java)** — provides the shared `java.net.http.HttpClient` bean (named `httpClient`, deliberately distinct from the `NseHttpClient` component to avoid a Spring bean-name collision) with an in-memory `CookieManager`.
3. **[client/NseHttpClient.java](markets-ref-data/src/main/java/com/marketpulse/refdata/client/NseHttpClient.java)** — mirrors the old Python `NSEHttpClient`: on construction it `GET`s `nse.base-url` to establish session cookies (NSE requires this before archive downloads succeed), then exposes `downloadFile(url)`. A 4xx/5xx response raises `NseHttpException` carrying the status code.
4. **[service/EquityCsvFilter.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/EquityCsvFilter.java)** — parses the raw Bhavcopy CSV (Apache Commons CSV) and keeps only rows where `SERIES == "EQ"`.
5. **[service/BhavcopyService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BhavcopyService.java)** — `@Transactional`; orchestrates download → filter → parse to `EquityPrice` entities → upsert into TimescaleDB, and reads records back for the API. Nothing is written to local disk; the `sec_bhavdata_full_DDMMYYYY.csv` name only identifies the *remote* archive file. A 404 from `NseHttpClient` is treated as an expected "holiday or not yet published" case (`DownloadResult.Status.NOT_FOUND`), not a failure.
6. **[entity/](markets-ref-data/src/main/java/com/marketpulse/refdata/entity/) + [repository/](markets-ref-data/src/main/java/com/marketpulse/refdata/repository/)** — `EquityPrice` (composite `EquityPriceId` of `trade_date, symbol`) and `EquitySymbol`. Both repositories pair a Spring Data interface with a hand-written `*RepositoryImpl` providing `upsertAll` batch upserts, so re-downloading a date updates rows instead of duplicating or failing on the primary key.
7. **[db/migration/](markets-ref-data/src/main/resources/db/migration/)** — Flyway owns the schema and runs at startup (`ddl-auto: validate`, so entities must match the SQL). `V1__create_equity_price_hypertable.sql` creates the extension, both tables, and promotes `equity_price` to a hypertable on `trade_date`. Schema changes go in a new `V2__*.sql`, never by editing `V1`.
8. **[controller/BhavcopyController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BhavcopyController.java)** — REST API: `POST /api/v1/bhavcopy/download?date=...` (triggers a download; omitted `date` defaults to today, or to the preceding Friday via the package-private `lastWeekday()` helper if today is a Saturday/Sunday) and `GET /api/v1/bhavcopy/{date}` (returns saved equity rows as JSON, 404 if not yet downloaded).
9. **[scheduler/BhavcopyScheduler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/scheduler/BhavcopyScheduler.java)** — `@Scheduled(cron = "${nse.scheduler.cron}", zone = "${nse.scheduler.zone}")`, replacing the old Python cron/Task Scheduler entry point. **The `zone` is pinned explicitly to `Asia/Kolkata`** — without it, a containerized JVM defaults to UTC and "19:00" would fire at the wrong wall-clock time. Disable via `nse.scheduler.enabled=false`.
10. **[service/BackfillService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillService.java)** — bulk historical loader. Walks the weekdays in a date range and calls `BhavcopyService.downloadBhavcopy` once per date. **Deliberately a separate bean**: calling across a bean boundary means the `@Transactional` proxy on `downloadBhavcopy` actually applies, so each date commits independently and a failure late in a multi-year run can't roll back the days already loaded. Runs `@Async` on a single-thread `backfillExecutor` so two backfills never hit NSE at once. Skips dates already in `equity_price` and dates any prior job recorded `NOT_FOUND` (holidays), which is what makes a re-POST of the same range resume cheaply; `force=true` bypasses both.
11. **[controller/BackfillController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BackfillController.java)** — `POST /api/v1/bhavcopy/backfill?from=&to=[&force=]` (202 + jobId, 409 if one is already active), `GET /api/v1/bhavcopy/backfill/{jobId}`, `GET /api/v1/bhavcopy/backfill`. Rejects `from > to`, a future `to`, and any `from` before `nse.backfill.earliest-date`.
12. **[service/BackfillStartupReconciler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java)** — on boot, marks PENDING/RUNNING jobs `INTERRUPTED`; the JVM that owned them is gone. Does **not** auto-resume, so a restart loop can't silently generate ~90 minutes of NSE traffic. Recovery is re-POSTing the same range.

### Deployment: why this must run continuously

Unlike the old Python script (invoked once daily by an external OS scheduler and then exiting), `BhavcopyScheduler`'s trigger lives *inside* the JVM process — the app must stay running 24/7 for the daily 19:00 IST job to fire at all. This is what [Dockerfile](markets-ref-data/Dockerfile) + [docker-compose.yml](markets-ref-data/docker-compose.yml) are for: `restart: unless-stopped` keeps the container alive across crashes/reboots (as long as Docker Desktop/daemon itself is running). `TZ=Asia/Kolkata` is also set at the container level as a second layer on top of the explicit Spring `zone`.

Compose brings up two services: `timescaledb` (published on 5432, gated by a `pg_isready` healthcheck that `markets-ref-data` waits on via `depends_on: condition: service_healthy`) and `markets-ref-data` (published on 8081). Market data persists in the `timescaledb-data` named volume, which survives `docker compose down` and image rebuilds — `docker compose down -v` wipes it.

### Testing conventions

JUnit 5 + Mockito + AssertJ; controller tests use `@WebMvcTest` + `MockMvc` + `@MockBean`. No live HTTP calls or real NSE data are used:
- **[client/NseHttpClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/NseHttpClientTest.java)** mocks `java.net.http.HttpClient` directly (it's injected, not constructed internally, specifically so this is mockable) and distinguishes the session-init call from the download call by inspecting the request URI in `thenAnswer`, since matching on `BodyHandlers.discarding()`/`ofByteArray()` by equality doesn't work (fresh lambda instances each call).
- **[service/BhavcopyServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java)** mocks `NseHttpClient` *and both repositories*, then asserts on the entities handed to `upsertAll` via `ArgumentCaptor` — no database involved.
- **[service/BhavcopyServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java)** is the one test that touches a real database: `@SpringBootTest` + `@Testcontainers` spinning up `timescale/timescaledb:2.17.2-pg16` (wired in by `@ServiceConnection`, so no manual datasource properties) with `NseHttpClient` as a `@MockBean`. It covers the actual Flyway migration, hypertable creation, and upsert-on-re-download behavior. Needs a running Docker daemon, and must be invoked by name — see the Commands note above.
- **[controller/BhavcopyControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/BhavcopyControllerTest.java)** covers the REST layer via `MockMvc`, plus direct unit tests of the package-private `BhavcopyController.lastWeekday()` static helper (Saturday/Sunday roll back to Friday, weekdays unchanged) rather than trying to mock `LocalDate.now()`.
- **[service/BackfillServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java)** mocks `BhavcopyService` and all three repositories, and sets `delay-ms` to 0 so the throttle doesn't slow the suite. Covers weekend exclusion, both skip sets, `force`, per-date failure isolation, and the consecutive-failure circuit breaker.
- **[service/BackfillServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java)** is the second Testcontainers test — same opt-in rules as `BhavcopyServiceIT`. Covers the `V2` migration and skip-on-re-run.

### Known operational constraints (carried over from the original Python research)

- NSE aggressively blocks scraping; requests without proper headers return `401`/`403`.
- A session must be established by visiting `nseindia.com` first to obtain valid cookies before hitting archive file URLs directly — this is why `NseHttpClient` performs a warm-up `GET` in its constructor.
- Realistic browser `User-Agent`/`Accept`/`Accept-Language` headers (see `nse.headers` in `application.yml`) are required to bypass these restrictions.
- The `sec_bhavdata_full_DDMMYYYY.csv` archive pattern only goes back to **2019-10-01** (~6.9 years). Earlier dates 404. Older history exists under NSE's legacy `cm<DD><MON><YYYY>bhav.csv.zip` path but lacks the delivery columns this schema requires.
- **`sec_bhavdata_full_30092019.csv` returns HTTP 200 with rows dated `27-Jun-2019`.** `BhavcopyService` therefore validates the CSV's `DATE1` column against the requested date and refuses to persist a mismatch — without that guard, `trade_date` is stamped from the requested date and bad data lands silently. This is why `nse.backfill.earliest-date` is 2019-10-01, not 2019-09-30.
- `GET /api/v1/bhavcopy/backfill` (the list endpoint) issues one query per job returned — up to 51 for a full page of 50 — because the per-status counts are derived by loading each job's date rows. Accepted deliberately: it's an operator-driven endpoint, hit a handful of times, and correctness isn't at stake. If job volume grows, replace it with a `GROUP BY job_id, status` aggregate.
