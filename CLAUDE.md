# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarketPulse is a multi-module project for enterprise/professional-level markets data tracking and analysis, split CQRS-style across three modules that share one TimescaleDB database:
- **`markets-ref-data`** — the write/ingestion side. A Spring Boot microservice that fetches daily NSE Bhavcopy EOD equity prices (on a schedule, on demand, or in bulk via the backfill API) and, on demand, company fundamentals from Yahoo Finance, persisting all of it to TimescaleDB. Owns the schema via Flyway.
- **`stock-discovery`** — the read/query side. A read-only Spring Boot microservice against the same database (`ddl-auto: none`, no Flyway) exposing search/history/fundamentals APIs for the UI.
- **`markets-ui`** — the frontend. A React + Vite + TypeScript app translated from the "Strata" design, calling `stock-discovery` for real data.
- **`markets-admin`** — the operations console. A stateless Spring Boot microservice serving one admin page covering every service in the system, driven by a configured registry.

Future modules (Watchlist, Portfolio, Alerts, News, Identity — per the project's event-storming diagram) are expected to live as further sibling directories.

`markets-ref-data` was originally a Python script under this same directory name; it was rewritten in Java/Spring Boot to run as a long-lived, continuously-deployed service rather than a script invoked by an external scheduler. `stock-discovery` was built from scratch following the same Java/Spring Boot conventions (including the Spring Boot 4.x quirks noted below) so the two backend services stay consistent.

## Commands

Each backend module has its own commands, run from its own directory. Requires JDK 25, Maven, and Docker (for TimescaleDB — both backend services need a live Postgres/TimescaleDB connection even for local, non-Docker runs).

```powershell
cd markets-ref-data

# Run the full test suite (unit tests only - see note below)
mvn test

# Run a single test class
mvn test -Dtest=BhavcopyServiceTest

# Run a single test method
mvn test -Dtest=BhavcopyServiceTest#treats404AsNotFoundRatherThanFailure

# Run either Testcontainers integration test (opt-in, needs a running Docker daemon)
mvn test -Dtest=BhavcopyServiceIT -DfailIfNoTests=false
mvn test -Dtest=BackfillServiceIT -DfailIfNoTests=false

# Run the app locally in the foreground (needs TimescaleDB reachable at localhost:5432)
docker compose -f ../docker-compose.yml up -d timescaledb   # database only
mvn spring-boot:run

# Build a runnable jar
mvn package
java -jar target/markets-ref-data-0.1.0-SNAPSHOT.jar

# Build and run continuously via Docker (recommended - see "Deployment" below)
# run from the repo root, not this directory - see "Deployment" below
cd ..
docker compose up -d --build
```

There is no linter/formatter config in this repo yet. **Neither `BhavcopyServiceIT` nor `BackfillServiceIT` is picked up by `mvn test`** — Surefire's default pattern only matches `*Test.java`, and no Failsafe plugin is configured, so the Testcontainers tests only run when named explicitly (see above). They require a working local Docker environment reachable by the Testcontainers Java library specifically, which has been flaky on this Windows/Docker Desktop setup even when the Docker CLI itself works fine. The default `mvn test` run is 54 tests and needs neither Docker nor network.

**The app cannot start without a reachable TimescaleDB** — Flyway migrates on boot and JPA runs with `ddl-auto: validate`. Connection settings come from `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`, defaulting to `jdbc:postgresql://localhost:5432/marketpulse` (which the composed `timescaledb` container serves on the published port).

`stock-discovery/` supports the same `mvn test` / `mvn test -Dtest=...` / `mvn spring-boot:run` / `mvn package` commands (port 8082, no Testcontainers/IT test in this module since it has no Flyway migrations of its own to exercise).

`markets-ui/` uses npm instead of Maven:
```powershell
cd markets-ui
npm install
npm run dev     # Vite dev server, needs stock-discovery reachable at VITE_API_BASE_URL (.env)
npm run build   # tsc -b && vite build, output in dist/
```

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
11. **[service/BackfillService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillService.java)** — bulk historical loader. Walks the weekdays in a date range and calls `BhavcopyService.downloadBhavcopy` once per date. **Deliberately a separate bean**: calling across a bean boundary means the `@Transactional` proxy on `downloadBhavcopy` actually applies, so each date commits independently and a failure late in a multi-year run can't roll back the days already loaded. Runs `@Async` on a single-thread `backfillExecutor` so two backfills never hit NSE at once. Skips dates already in `equity_price` and dates any prior job recorded `NOT_FOUND` (holidays), which is what makes a re-POST of the same range resume cheaply; `force=true` bypasses both.
12. **[controller/BackfillController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BackfillController.java)** — `POST /api/v1/bhavcopy/backfill?from=&to=[&force=]` (202 + jobId, 409 if one is already active), `GET /api/v1/bhavcopy/backfill/{jobId}`, `GET /api/v1/bhavcopy/backfill`. Rejects `from > to`, a future `to`, and any `from` before `nse.backfill.earliest-date`.
13. **[service/BackfillStartupReconciler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java)** — on boot, marks PENDING/RUNNING jobs `INTERRUPTED`; the JVM that owned them is gone. Does **not** auto-resume, so a restart loop can't silently generate ~90 minutes of NSE traffic. Recovery is re-POSTing the same range.
14. **[config/AsyncConfig.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/AsyncConfig.java)** — `@EnableAsync` plus the single-thread `backfillExecutor`. It **also re-declares Boot's default executor under both `applicationTaskExecutor` and `taskExecutor`**: Boot's own `TaskExecutorConfiguration` is `@ConditionalOnMissingBean(Executor.class)`, so declaring `backfillExecutor` alone would suppress the default and silently drop any future unqualified `@Async` onto an unbounded `SimpleAsyncTaskExecutor`.

### Package layout (`com.marketpulse.stockdiscovery`, in `stock-discovery/`)

This service is deliberately the mirror image of `markets-ref-data`: same conventions, same Spring Boot 4.x quirks, but read-only.

1. **`entity/EquitySymbol.java`, `entity/EquityPrice.java`+`EquityPriceId.java`, `entity/EquityFundamentals.java`** — read-mapped copies of `markets-ref-data`'s entities (identical `@Table`/`@Column` mappings against the same tables), each with a public all-args constructor for clean test-fixture construction.
2. **`repository/EquitySearchRepository.java`** — `JdbcTemplate`-based (not JPA, for the `LEFT JOIN`): `... FROM equity_symbol s LEFT JOIN equity_fundamentals f ON f.symbol = s.symbol WHERE s.symbol ILIKE ? OR f.company_name ILIKE ? ORDER BY s.symbol LIMIT ?`, returning `model/EquitySearchResult.java`.
3. **`repository/EquityPriceRepository.java`** / **`repository/EquityFundamentalsRepository.java`** — plain `JpaRepository`s, read-only usage.
4. **`service/StockDiscoveryService.java`** — `search(query, limit)`, `getHistory(symbol)` (→ `model/EquityRecord.java`, same shape as `markets-ref-data`'s DTO of the same name), `getFundamentals(symbol)` (→ `model/FundamentalsView.java`).
5. **`controller/StockDiscoveryController.java`** — `GET /api/v1/stocks/search?q=&limit=20`, `GET /api/v1/stocks/{symbol}/history` (404 if empty), `GET /api/v1/stocks/{symbol}/fundamentals` (404 if absent).
6. **`config/CorsConfig.java`** — `WebMvcConfigurer` allowing the browser-based `markets-ui` origin(s) (`app.cors.allowed-origins`, comma-separated) to call `/api/**`.

This service has **no Flyway dependency at all** and `spring.jpa.hibernate.ddl-auto: none` — it must never migrate or alter the schema `markets-ref-data` owns.

### `markets-ui` frontend (React + Vite + TypeScript)

Translates the "Strata" design (a Claude Design canvas project — `Strata.dc.html` + `support.js`, a proprietary `<x-dc>`/`DCLogic` format that only renders inside Claude Design's own editor, not deployable as-is) into a real React app calling `stock-discovery`.

- **`src/lib/chart.ts`** — hand-rolled SVG chart math ported from the design (`coords()`, Catmull-Rom `line()` smoothing, `areaPath()`, `candles()`) rather than a charting library dependency, now fed real O/H/L/C data instead of a random walk. `money()`/`compact()` formatters were adapted from the design's USD/T/B/M to Indian Rupee (₹) with Lakh/Crore units, since this is NSE data.
- **`src/theme.css`** — full port of Strata's CSS custom properties (dark `:root` + `[data-theme='light']`), fonts (Instrument Serif, Space Grotesk, JetBrains Mono via Google Fonts), plus reusable component classes (`.card`, `.grid-table-row`, `.pill-tabs`, `.stat-tile`, etc.) chosen over a 1:1 inline-style transcription of the original design for maintainability.
- **`src/pages/StockDetailPage.tsx`** — the primary real-data screen: fetches `getHistory`/`getFundamentals` from `stock-discovery`, Line/Candles chart toggle, Key Statistics grid (real price fields + fundamentals when present), About section with graceful degradation when fundamentals haven't been fetched yet (distinct "not yet fetched" state vs. "still loading").
- **`src/components/Layout.tsx`** — header with a real debounced (250ms) search box (`GET /api/v1/stocks/search`) navigating to `/stock/:symbol`, plus the sidebar nav. The sidebar's "Data Source: NSE Bhavcopy · stock-discovery" info card deliberately replaces Strata's fake "Day P/L" card, since the sidebar is persistent chrome across every page including the real Stock Detail page.
- **`src/pages/{Dashboard,Watchlist,Markets,Portfolio,News,Alerts}Page.tsx`** — everything else. Real-looking UI, but data comes from `src/lib/mockData.ts` (Strata's own `mulberry32`-seeded random walk, ported as-is except `indices` swapped to Indian indices — NIFTY 50/SENSEX/NIFTY BANK/INDIA VIX — instead of Strata's S&P 500/Nasdaq/Dow/VIX). Every mock section is labeled with a `<MockDataNote />` badge ("demo data") — **this is a deliberate, honest scope boundary**, not an oversight: Watchlist, Portfolio, Alerts, News, and Market Data are separate bounded contexts (per the project's event-storming diagram) with no backing service built yet.

### Data model

- **`equity_symbol`** — dimension table (symbol, first/last-seen dates). Correct normalization boundary: slowly-changing entity metadata, separate from time-series facts.
- **`equity_price`** — TimescaleDB hypertable, `(trade_date, symbol)` composite PK, partitioned by `trade_date`. Deliberately **not** split further by column group (e.g. price vs. volume/delivery stats) — OHLCV-style data is conventionally kept as one row per symbol per day since it's always written/queried together; splitting would mean joining two hypertables for what's currently a single-row read, for no real benefit at this row volume.
- **`equity_fundamentals`** — plain table (not a hypertable), symbol PK with FK to `equity_symbol`. Fundamentals are a slowly-changing snapshot per symbol, not a daily time series, so this follows the `equity_symbol` dimension-table pattern rather than `equity_price`'s.
- **`backfill_job` / `backfill_job_date`** — plain tables tracking bulk-load jobs and their per-date outcome. The per-date rows are what make a re-POST of the same range cheap (already-loaded dates and previously-`NOT_FOUND` holidays are skipped) and what `BackfillStartupReconciler` reconciles after a restart.
- Flyway migrations in `db/migration/`: `V1` creates the hypertable, `V2` adds `equity_fundamentals`, `V3` adds the backfill job tables, `V4` repairs `equity_symbol` first/last-seen dates.

**On migration numbering:** `V3`/`V4` were `V2`/`V3` on the backfill branch and were renumbered when it merged, because `V2__create_equity_fundamentals.sql` had already been applied to the live database and a version number cannot move once Flyway has recorded it. If you developed against the pre-merge branch, your local `flyway_schema_history` still has `V2 = create backfill job` and will not reconcile — recreate that database (`docker compose down -v`) rather than editing history. **Never renumber or edit an applied migration; always add a new one.**

### Deployment: why this must run continuously

`BhavcopyScheduler`'s trigger lives *inside* the JVM process — the app must stay running 24/7 for the daily 19:00 IST job to fire at all. **[docker-compose.yml](docker-compose.yml) lives at the repo root** (moved there once `stock-discovery` and `markets-ui` needed their own build contexts alongside it) and defines four services: `timescaledb`, `markets-ref-data` (port 8081, waits on the DB healthcheck), `stock-discovery` (port 8082, also waits on the DB healthcheck), and `markets-ui` (port 3000 → nginx 80, waits on `stock-discovery`). `restart: unless-stopped` on every service keeps them alive across crashes/reboots (as long as Docker Desktop/daemon itself is running), and the `timescaledb-data` named volume persists price/fundamentals data across container recreation. `TZ=Asia/Kolkata` is also set at the `markets-ref-data`/`stock-discovery` container level as a second layer on top of the explicit Spring `zone`. Each module still has its own `Dockerfile`; only the compose file itself is shared.

### Testing conventions

JUnit 5 + Mockito + AssertJ; controller tests use `@WebMvcTest` + `MockMvc` + `@MockitoBean` (not the deprecated-and-now-removed `@MockBean`). No live HTTP calls to NSE/Yahoo, or a real DB, are used in the default `mvn test` run (`BhavcopyServiceIT` is the one exception — see Commands above):
- **[client/NseHttpClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/NseHttpClientTest.java)** / **[client/YahooFinanceClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/YahooFinanceClientTest.java)** mock `java.net.http.HttpClient` directly (it's injected, not constructed internally, specifically so this is mockable) and distinguish calls by inspecting the request URI in `thenAnswer`, since matching on `BodyHandlers.discarding()`/`ofByteArray()`/`ofString()` by equality doesn't work (fresh lambda instances each call).
- **[service/BhavcopyServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java)** / **[service/FundamentalsServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/FundamentalsServiceTest.java)** mock the repository layer directly (`equityPriceRepository`, `equitySymbolRepository`, `equityFundamentalsRepository`) rather than hitting a real DB.
- **[service/BhavcopyServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java)** is the real-DB integration test, using `@Testcontainers` + `PostgreSQLContainer` (image `timescale/timescaledb:...`) — exercises the actual Flyway migration, hypertable, and upsert-not-duplicate behavior end-to-end.
- **[service/BackfillServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java)** mocks `BhavcopyService` and all three repositories, and sets `delay-ms` to 0 so the throttle doesn't slow the suite. Covers weekend exclusion, both skip sets, `force`, per-date failure isolation, and the consecutive-failure circuit breaker. **It runs on a fixed `Clock` pinned to Wednesday 2026-08-12** — see the date-sensitivity note below.
- **[service/BackfillServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java)** is the second Testcontainers test — same opt-in rules as `BhavcopyServiceIT`. Covers the backfill migration, skip-on-re-run, holiday recording, and the out-of-order `equity_symbol` seen-window widening that backfills specifically provoke.
- **[controller/BhavcopyControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/BhavcopyControllerTest.java)** / **[controller/FundamentalsControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/FundamentalsControllerTest.java)** — `@WebMvcTest` slices **must** exclude `SecurityAutoConfiguration`, `UserDetailsServiceAutoConfiguration`, `SecurityFilterAutoConfiguration`, and `ServletWebSecurityAutoConfiguration` explicitly (see Spring Boot 4.x note below), or the test slice enforces real HTTP Basic auth and every request 401s/403s.

`stock-discovery` follows the identical pattern in its own `src/test/java/com/marketpulse/stockdiscovery/` tree — `StockDiscoveryServiceTest` mocks the three repositories directly, `StockDiscoveryControllerTest` is a `@WebMvcTest` with the same four Security auto-config exclusions. It has no Testcontainers/IT test since it owns no Flyway migrations to exercise end-to-end.

`markets-ui` has no automated test suite yet — verification is `npm run build` (type-checks via `tsc -b`) plus manual click-through.

**Date-sensitive logic takes an injected `Clock`, never `LocalDate.now()`.** `config/ClockConfig.java` supplies a `Clock` bean (`systemDefaultZone()`, which preserves the prior behaviour since the containers set `TZ=Asia/Kolkata`). **No business-logic `LocalDate.now()` remains** — `BackfillService` (NOT_FOUND trust window), `BackfillController` (future-date guard), `BhavcopyController` (date defaulting) and `BhavcopyScheduler` (which day to fetch) all take the clock.

This started as a bug: `BackfillService`'s trust window distrusts a NOT_FOUND newer than `today - 2`, and its test picked "the most recent weekday" to land inside that window. **On a Sunday no such weekday exists** — the last weekday is Friday, which is exactly `today - 2` and therefore already trusted — so the suite failed one day in seven, and no choice of date could fix it without controlling "today".

Fixed clocks in tests are chosen so existing dates keep their meaning: `BackfillServiceTest` pins Wednesday 2026-08-12 (trust boundary lands on 2026-08-10, leaving every other date in the class trusted exactly as before); `BackfillControllerTest` pins the same day to test the future-date boundary precisely rather than via a far-future date that passes under any clock; `BhavcopyControllerTest` pins **Sunday** 2026-08-09 so `download()`'s weekend rollback is proven end to end, not just through the static `lastWeekday` helper.

`@WebMvcTest` does not load `ClockConfig` (it is a plain `@Configuration`), so each controller slice supplies its own `Clock` bean in a `@TestConfiguration` — without it the slice cannot construct the controller at all.

Audit timestamps are deliberately **not** converted: `Instant.now()` in `BackfillJob`/`BackfillJobDate` and `OffsetDateTime.now()` in `FundamentalsService` record when something happened rather than deciding behaviour, and JPA entities are not Spring beans, so injecting a clock there would mean threading it through constructors for no testability gain.

### Known operational constraints (carried over from the original Python research)

- NSE aggressively blocks scraping; requests without proper headers return `401`/`403`. A session must be established by visiting `nseindia.com` first — this is why `NseHttpClient` performs a warm-up `GET` in its constructor. Realistic browser `User-Agent`/`Accept`/`Accept-Language` headers (see `nse.headers` in `application.yml`) are required.
- NSE's Bhavcopy/quote data carries price and trading-activity fields only — **no market cap, P/E, sector, or company description**. That's why fundamentals come from a separate source (Yahoo Finance) entirely.
- Yahoo Finance's unofficial API needs the cookie+crumb handshake described above, and rate-limits hard under repeated manual testing — expect to hit `429`s if iterating quickly during development.
- The `sec_bhavdata_full_DDMMYYYY.csv` archive pattern only goes back to **2019-10-01**. Earlier dates 404. Older history exists under NSE's legacy `cm<DD><MON><YYYY>bhav.csv.zip` path but lacks the delivery columns this schema requires — hence `nse.backfill.earliest-date`.
- **`sec_bhavdata_full_30092019.csv` returns HTTP 200 with rows dated `27-Jun-2019`.** `BhavcopyService` therefore validates the CSV's `DATE1` column against the requested date and refuses to persist a mismatch — without that guard, `trade_date` is stamped from the requested date and bad data lands silently. This is why `earliest-date` is 2019-10-01, not 2019-09-30.
- `GET /api/v1/bhavcopy/backfill` (the list endpoint) issues one query per job returned — up to 51 for a full page of 50 — because the per-status counts are derived by loading each job's date rows. Accepted deliberately: it's an operator-driven endpoint, hit a handful of times, and correctness isn't at stake. If job volume grows, replace it with a `GROUP BY job_id, status` aggregate.

### Spring Boot 4.x / Java 25 upgrade notes

This project was upgraded from Java 21 / Spring Boot 3.3.4 to **Java 25 / Spring Boot 4.1.1**. Spring Boot 4 is a genuinely major upgrade (Spring Framework 7, extreme module splitting) with several non-obvious `pom.xml` consequences specific to this project, worth knowing before touching dependencies again:
- **Flyway**: raw `flyway-core` no longer auto-configures Flyway in Spring Boot 4 — needs `spring-boot-starter-flyway` instead.
- **Testcontainers**: Spring Boot 4's own dependency management no longer supplies `org.testcontainers:junit-jupiter`/`postgresql` versions — the `<dependencyManagement>` block explicitly imports `testcontainers-bom` now.
- **Jackson**: Spring Boot 4 defaults to Jackson 3 (`tools.jackson.*`) via `spring-boot-starter-jackson`, pulled in transitively by `spring-boot-starter-web`. `YahooFinanceClient`/`FundamentalsService` use the classic Jackson 2 API (`com.fasterxml.jackson.databind`), so `spring-boot-jackson2` is added explicitly to keep that on the classpath alongside Jackson 3 (both coexist fine; Spring resolves by the declared type at each injection point).
- **`@MockBean` is gone** (not just deprecated) — replaced by `org.springframework.test.context.bean.override.mockito.MockitoBean` (from `spring-test` itself, not a Spring Boot module).
- **`@WebMvcTest` moved** from `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`, and its hard-coded auto-configuration import list references `org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration` **by class name even when the project has zero Spring Security dependency** — the class must be on the (test) classpath or `@WebMvcTest` fails to load at all. This project adds `spring-boot-security` + `spring-security-test` as **test-scoped only** dependencies purely to satisfy that reference, then explicitly excludes the actual servlet security auto-configurations in each `@WebMvcTest` annotation (see Testing conventions above) so tests aren't forced through real HTTP Basic auth. The real, deployed app has no Spring Security on its runtime classpath at all — this is a test-only quirk of Spring Boot 4.1.1's module structure, not a production security posture change.
- **`spring-boot-starter-test`** now excludes most test-slice support (`@WebMvcTest`, security test helpers, etc.) by default; this project uses **`spring-boot-starter-test-classic`** instead, which bundles the granular per-feature test modules back together for less migration churn.
- **springdoc/Swagger**: both backend services use `springdoc-openapi-starter-webmvc-ui` **3.1.0**. The **3.x line is the Spring Boot 4 line** (its parent is `spring-boot-starter-parent` 4.1.0); the widely-referenced 2.x line targets Spring Boot 3 and will not work here. Version is pinned via a `<springdoc.version>` property in each pom since Spring Boot's dependency management doesn't cover springdoc. It coexists fine with this project's dual Jackson 2 + Jackson 3 classpath, and doesn't leak into the `@WebMvcTest` slices (springdoc's auto-configuration isn't in that slice's import list).

### `markets-admin` operations console

A **separate microservice** (port 8083) serving one console at `/` for the whole system, rather than an admin page embedded in each service. That split is the point: the console is a system-level view, and duplicating a page into every service would mean editing N services to add one link.

1. **`config/AdminProperties.java`** — `@ConfigurationProperties(prefix = "admin")`, a record binding `admin.services` from `application.yml`. **This is the only place the console's contents are configured** — adding a future module to the console is a YAML entry, not a code change, and nothing is added to the service being listed.
2. **`service/ServiceStatusService.java`** — reads the registry and enriches each entry with a live health probe. Probes run **in parallel** (`CompletableFuture`): with a per-probe timeout, checking serially would make page load the *sum* of every unreachable service's timeout — exactly the situation the console exists to report on.
3. **`controller/AdminController.java`** — `GET /api/v1/admin/services`, `GET /api/v1/admin/services/{id}`. The list endpoint is **always 200**; a dead service is a `DOWN` entry in the payload, not an error on the call.
4. **`static/index.html`** — the console, rendered from that API. No build step and no external dependencies: the Strata palette is inlined and fonts are system stacks, so it renders with no network access.

**Each service carries two URLs, and they are not interchangeable.** `internal-url` is what `markets-admin` probes server-side, so under Docker it is the compose DNS name (`http://stock-discovery:8082`); `external-url` is baked into the links a browser follows, so it must be host-reachable (`http://localhost:8082`). Collapsing them breaks whichever consumer is on the far side — the compose file overrides only the internal ones.

**Why probing is server-side:** the browser cannot resolve compose-internal hostnames, and fetching other services cross-origin from the console page would be blocked by CORS. The probe belongs on the server for both reasons.

Health classification is deliberately tolerant of non-Actuator services: a parseable `status` field wins over the HTTP code (Actuator answers `503` with a `DOWN` body), and a plain 2xx with no JSON counts as UP — which is how `markets-ui`, static nginx with no Actuator, is covered at all. Connect timeouts and response timeouts are reported distinctly, since they fire on different clocks and have different fixes.

**This service has no database and no `depends_on`**, deliberately: it must start and report on the others precisely when they are broken.

### API documentation (Swagger / OpenAPI)

Both backend services expose Swagger UI at **`/swagger-ui.html`** and the raw OpenAPI 3 document at **`/v3/api-docs`** (`markets-ref-data` on 8081, `stock-discovery` on 8082). `markets-ui` is a static SPA and has none.

Each service has a `config/OpenApiConfig.java` supplying document-level title/description only — springdoc derives the endpoints themselves from the Spring MVC annotations. Controllers carry `@Tag`/`@Operation`/`@ApiResponse` annotations that deliberately document this project's **non-obvious status-code semantics**, which springdoc cannot infer: `POST /api/v1/bhavcopy/download` returns **404 for a market holiday** (an expected outcome, not an error) and **502** for an upstream NSE failure; `POST /api/v1/fundamentals/refresh` always returns **200** with a per-symbol mix of SUCCESS/NOT_FOUND/FAILURE because errors are isolated per symbol; and `stock-discovery`'s fundamentals **404 means "not fetched yet"**, not "no such symbol". Keep these annotations in sync when changing controller behavior — they're the part of the docs that carries real information.
