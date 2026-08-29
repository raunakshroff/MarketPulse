# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarketPulse is a multi-module project for enterprise/professional-level markets data tracking and analysis. It currently hosts one module, `markets-ref-data`, a Spring Boot microservice that fetches daily NSE Bhavcopy EOD equity prices and, on demand, company fundamentals from Yahoo Finance, persisting both to TimescaleDB. Future modules are expected to live as sibling directories alongside `markets-ref-data`.

This module was originally a Python script under this same directory name; it was rewritten in Java/Spring Boot to run as a long-lived, continuously-deployed service rather than a script invoked by an external scheduler.

## Commands

All commands below are run from the `markets-ref-data/` directory. Requires JDK 25, Maven, and Docker (for TimescaleDB — the app needs a live Postgres/TimescaleDB connection even for local, non-Docker runs).

```powershell
cd markets-ref-data

# Run the full test suite
mvn test

# Run a single test class
mvn test -Dtest=BhavcopyServiceTest

# Run a single test method
mvn test -Dtest=BhavcopyServiceTest#treats404AsNotFoundRatherThanFailure

# Run the app locally in the foreground (needs TimescaleDB reachable at localhost:5432)
mvn spring-boot:run

# Build a runnable jar
mvn package
java -jar target/markets-ref-data-0.1.0-SNAPSHOT.jar

# Build and run continuously via Docker (recommended - see "Deployment" below)
docker compose up -d --build
```

There is no linter/formatter config in this repo yet. `BhavcopyServiceIT` (Testcontainers-backed) is **not** picked up by `mvn test` — Surefire's default pattern only matches `*Test.java`, and no Failsafe plugin is configured. Run it explicitly: `mvn test -Dtest=BhavcopyServiceIT -DfailIfNoTests=false` (requires a working local Docker environment reachable by the Testcontainers Java library specifically, which has been flaky on this Windows/Docker Desktop setup even when the Docker CLI itself works fine).

## Architecture

### Package layout (`com.marketpulse.refdata`)

1. **[config/NseProperties.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/NseProperties.java)** / **[config/YahooFinanceProperties.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/YahooFinanceProperties.java)** — `@ConfigurationProperties` for each external data source (`nse.*`, `yahoo-finance.*`), both registered via `@EnableConfigurationProperties` on `RefDataApplication`. NSE header keys use YAML bracket syntax (`"[User-Agent]"`) so hyphenated names bind literally.
2. **[config/HttpClientConfig.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/HttpClientConfig.java)** — the single shared `java.net.http.HttpClient` bean (named `httpClient`, deliberately distinct from the `NseHttpClient` component to avoid a Spring bean-name collision) with an in-memory `CookieManager`, reused by both `NseHttpClient` and `YahooFinanceClient` (cookies are domain-scoped, so they don't collide).
3. **[client/NseHttpClient.java](markets-ref-data/src/main/java/com/marketpulse/refdata/client/NseHttpClient.java)** — on construction it `GET`s `nse.base-url` to establish session cookies (NSE requires this before archive downloads succeed), then exposes `downloadFile(url)`. A 4xx/5xx response raises `NseHttpException` carrying the status code.
4. **[client/YahooFinanceClient.java](markets-ref-data/src/main/java/com/marketpulse/refdata/client/YahooFinanceClient.java)** — Yahoo's unofficial `quoteSummary` endpoint requires a cookie+crumb handshake (a plain request 401s with `"Invalid Crumb"`); the client warms up a cookie, exchanges it for a crumb once at construction (held for the JVM's lifetime — a restart re-establishes it if it goes stale), then includes both on every `getFundamentals(symbol)` call. **The unofficial API rate-limits aggressively** under repeated testing (`429`, plain-text `"Edge: Too Many Requests"` body rather than JSON) — `fetchCrumb()` checks the status code explicitly before treating the response body as a crumb, to fail fast with a clear log message instead of a confusing downstream JSON-parse error.
5. **[service/EquityCsvFilter.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/EquityCsvFilter.java)** — parses the raw Bhavcopy CSV (Apache Commons CSV) and keeps only rows where `SERIES == "EQ"`.
6. **[service/BhavcopyService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BhavcopyService.java)** — orchestrates download → filter → upsert into TimescaleDB (`equity_price` hypertable + `equity_symbol` dimension table), and reads records back for the API. A 404 from `NseHttpClient` is treated as an expected "holiday or not yet published" case (`DownloadResult.Status.NOT_FOUND`), not a failure. Re-running for a date that already has data **overwrites rather than duplicates** (idempotent JDBC upsert, not JPA save).
7. **[service/FundamentalsService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/FundamentalsService.java)** — fetches fundamentals per symbol via `YahooFinanceClient`, upserts into `equity_fundamentals`. **Per-symbol error isolation**: one bad/unknown symbol in a batch logs and continues rather than failing the rest (`FundamentalsResult.Status` per symbol). On-demand only — no scheduled batch refresh of all known symbols (a deliberate scope decision, not an oversight).
8. **[controller/BhavcopyController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BhavcopyController.java)** — `POST /api/v1/bhavcopy/download?date=...` (omitted `date` defaults to today, or the preceding Friday via the package-private `lastWeekday()` helper if today is a Saturday/Sunday) and `GET /api/v1/bhavcopy/{date}`.
9. **[controller/FundamentalsController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/FundamentalsController.java)** — `POST /api/v1/fundamentals/refresh?symbols=A,B` (comma-separated, Spring auto-splits into `List<String>`) and `GET /api/v1/fundamentals/{symbol}`.
10. **[scheduler/BhavcopyScheduler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/scheduler/BhavcopyScheduler.java)** — `@Scheduled(cron = "${nse.scheduler.cron}", zone = "${nse.scheduler.zone}")`. **The `zone` is pinned explicitly to `Asia/Kolkata`** — without it, a containerized JVM defaults to UTC and "19:00" would fire at the wrong wall-clock time. Disable via `nse.scheduler.enabled=false`. Fundamentals has no equivalent scheduler.

### Data model

- **`equity_symbol`** — dimension table (symbol, first/last-seen dates). Correct normalization boundary: slowly-changing entity metadata, separate from time-series facts.
- **`equity_price`** — TimescaleDB hypertable, `(trade_date, symbol)` composite PK, partitioned by `trade_date`. Deliberately **not** split further by column group (e.g. price vs. volume/delivery stats) — OHLCV-style data is conventionally kept as one row per symbol per day since it's always written/queried together; splitting would mean joining two hypertables for what's currently a single-row read, for no real benefit at this row volume.
- **`equity_fundamentals`** — plain table (not a hypertable), symbol PK with FK to `equity_symbol`. Fundamentals are a slowly-changing snapshot per symbol, not a daily time series, so this follows the `equity_symbol` dimension-table pattern rather than `equity_price`'s.
- Flyway migrations in `db/migration/`: `V1` creates the hypertable, `V2` adds `equity_fundamentals`.

### Deployment: why this must run continuously

`BhavcopyScheduler`'s trigger lives *inside* the JVM process — the app must stay running 24/7 for the daily 19:00 IST job to fire at all. [Dockerfile](markets-ref-data/Dockerfile) + [docker-compose.yml](markets-ref-data/docker-compose.yml) define two services (`timescaledb` + the app, the app waiting on the DB's healthcheck); `restart: unless-stopped` on both keeps them alive across crashes/reboots (as long as Docker Desktop/daemon itself is running), and the `timescaledb-data` named volume persists price/fundamentals data across container recreation. `TZ=Asia/Kolkata` is also set at the container level as a second layer on top of the explicit Spring `zone`.

### Testing conventions

JUnit 5 + Mockito + AssertJ; controller tests use `@WebMvcTest` + `MockMvc` + `@MockitoBean` (not the deprecated-and-now-removed `@MockBean`). No live HTTP calls to NSE/Yahoo, or a real DB, are used in the default `mvn test` run (`BhavcopyServiceIT` is the one exception — see Commands above):
- **[client/NseHttpClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/NseHttpClientTest.java)** / **[client/YahooFinanceClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/YahooFinanceClientTest.java)** mock `java.net.http.HttpClient` directly (it's injected, not constructed internally, specifically so this is mockable) and distinguish calls by inspecting the request URI in `thenAnswer`, since matching on `BodyHandlers.discarding()`/`ofByteArray()`/`ofString()` by equality doesn't work (fresh lambda instances each call).
- **[service/BhavcopyServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java)** / **[service/FundamentalsServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/FundamentalsServiceTest.java)** mock the repository layer directly (`equityPriceRepository`, `equitySymbolRepository`, `equityFundamentalsRepository`) rather than hitting a real DB.
- **[service/BhavcopyServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java)** is the real-DB integration test, using `@Testcontainers` + `PostgreSQLContainer` (image `timescale/timescaledb:...`) — exercises the actual Flyway migration, hypertable, and upsert-not-duplicate behavior end-to-end.
- **[controller/BhavcopyControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/BhavcopyControllerTest.java)** / **[controller/FundamentalsControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/FundamentalsControllerTest.java)** — `@WebMvcTest` slices **must** exclude `SecurityAutoConfiguration`, `UserDetailsServiceAutoConfiguration`, `SecurityFilterAutoConfiguration`, and `ServletWebSecurityAutoConfiguration` explicitly (see Spring Boot 4.x note below), or the test slice enforces real HTTP Basic auth and every request 401s/403s.

### Known operational constraints (carried over from the original Python research)

- NSE aggressively blocks scraping; requests without proper headers return `401`/`403`. A session must be established by visiting `nseindia.com` first — this is why `NseHttpClient` performs a warm-up `GET` in its constructor. Realistic browser `User-Agent`/`Accept`/`Accept-Language` headers (see `nse.headers` in `application.yml`) are required.
- NSE's Bhavcopy/quote data carries price and trading-activity fields only — **no market cap, P/E, sector, or company description**. That's why fundamentals come from a separate source (Yahoo Finance) entirely.
- Yahoo Finance's unofficial API needs the cookie+crumb handshake described above, and rate-limits hard under repeated manual testing — expect to hit `429`s if iterating quickly during development.

### Spring Boot 4.x / Java 25 upgrade notes

This project was upgraded from Java 21 / Spring Boot 3.3.4 to **Java 25 / Spring Boot 4.1.1**. Spring Boot 4 is a genuinely major upgrade (Spring Framework 7, extreme module splitting) with several non-obvious `pom.xml` consequences specific to this project, worth knowing before touching dependencies again:
- **Flyway**: raw `flyway-core` no longer auto-configures Flyway in Spring Boot 4 — needs `spring-boot-starter-flyway` instead.
- **Testcontainers**: Spring Boot 4's own dependency management no longer supplies `org.testcontainers:junit-jupiter`/`postgresql` versions — the `<dependencyManagement>` block explicitly imports `testcontainers-bom` now.
- **Jackson**: Spring Boot 4 defaults to Jackson 3 (`tools.jackson.*`) via `spring-boot-starter-jackson`, pulled in transitively by `spring-boot-starter-web`. `YahooFinanceClient`/`FundamentalsService` use the classic Jackson 2 API (`com.fasterxml.jackson.databind`), so `spring-boot-jackson2` is added explicitly to keep that on the classpath alongside Jackson 3 (both coexist fine; Spring resolves by the declared type at each injection point).
- **`@MockBean` is gone** (not just deprecated) — replaced by `org.springframework.test.context.bean.override.mockito.MockitoBean` (from `spring-test` itself, not a Spring Boot module).
- **`@WebMvcTest` moved** from `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`, and its hard-coded auto-configuration import list references `org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration` **by class name even when the project has zero Spring Security dependency** — the class must be on the (test) classpath or `@WebMvcTest` fails to load at all. This project adds `spring-boot-security` + `spring-security-test` as **test-scoped only** dependencies purely to satisfy that reference, then explicitly excludes the actual servlet security auto-configurations in each `@WebMvcTest` annotation (see Testing conventions above) so tests aren't forced through real HTTP Basic auth. The real, deployed app has no Spring Security on its runtime classpath at all — this is a test-only quirk of Spring Boot 4.1.1's module structure, not a production security posture change.
- **`spring-boot-starter-test`** now excludes most test-slice support (`@WebMvcTest`, security test helpers, etc.) by default; this project uses **`spring-boot-starter-test-classic`** instead, which bundles the granular per-feature test modules back together for less migration churn.
