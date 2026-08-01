# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarketPulse is a multi-module project for enterprise/professional-level markets data tracking and analysis. It currently hosts one module, `markets-ref-data`, a Spring Boot microservice that handles external data extraction and reference data management (fetching daily NSE Bhavcopy EOD price files, filtered to equities). Future modules are expected to live as sibling directories alongside `markets-ref-data`.

This module was originally a Python script under this same directory name; it was rewritten in Java/Spring Boot to run as a long-lived, continuously-deployed service rather than a script invoked by an external scheduler.

## Commands

All commands below are run from the `markets-ref-data/` directory. Requires JDK 21 and Maven.

```powershell
cd markets-ref-data

# Run the full test suite
mvn test

# Run a single test class
mvn test -Dtest=BhavcopyServiceTest

# Run a single test method
mvn test -Dtest=BhavcopyServiceTest#treats404AsNotFoundRatherThanFailure

# Run the app locally in the foreground
mvn spring-boot:run

# Build a runnable jar
mvn package
java -jar target/markets-ref-data-0.1.0-SNAPSHOT.jar

# Build and run continuously via Docker (recommended - see "Deployment" below)
docker compose up -d --build
```

There is no linter/formatter config in this repo yet.

## Architecture

### Package layout (`com.marketpulse.refdata`)

1. **[config/NseProperties.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/NseProperties.java)** — `@ConfigurationProperties(prefix = "nse")`: base/archive URLs, `data-dir`, the browser-like `headers` map, and nested `scheduler.{enabled,cron,zone}`. Backed by [application.yml](markets-ref-data/src/main/resources/application.yml) — note header keys use YAML bracket syntax (`"[User-Agent]"`) so hyphenated names bind literally.
2. **[config/HttpClientConfig.java](markets-ref-data/src/main/java/com/marketpulse/refdata/config/HttpClientConfig.java)** — provides the shared `java.net.http.HttpClient` bean (named `httpClient`, deliberately distinct from the `NseHttpClient` component to avoid a Spring bean-name collision) with an in-memory `CookieManager`.
3. **[client/NseHttpClient.java](markets-ref-data/src/main/java/com/marketpulse/refdata/client/NseHttpClient.java)** — mirrors the old Python `NSEHttpClient`: on construction it `GET`s `nse.base-url` to establish session cookies (NSE requires this before archive downloads succeed), then exposes `downloadFile(url)`. A 4xx/5xx response raises `NseHttpException` carrying the status code.
4. **[service/EquityCsvFilter.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/EquityCsvFilter.java)** — parses the raw Bhavcopy CSV (Apache Commons CSV) and keeps only rows where `SERIES == "EQ"`.
5. **[service/BhavcopyService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BhavcopyService.java)** — orchestrates download → filter → write to `nse.data-dir` as `sec_bhavdata_full_DDMMYYYY.csv`, and reads records back for the API. A 404 from `NseHttpClient` is treated as an expected "holiday or not yet published" case (`DownloadResult.Status.NOT_FOUND`), not a failure.
6. **[controller/BhavcopyController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BhavcopyController.java)** — REST API: `POST /api/v1/bhavcopy/download?date=...` (triggers a download; omitted `date` defaults to today, or to the preceding Friday via the package-private `lastWeekday()` helper if today is a Saturday/Sunday) and `GET /api/v1/bhavcopy/{date}` (returns saved equity rows as JSON, 404 if not yet downloaded).
7. **[scheduler/BhavcopyScheduler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/scheduler/BhavcopyScheduler.java)** — `@Scheduled(cron = "${nse.scheduler.cron}", zone = "${nse.scheduler.zone}")`, replacing the old Python cron/Task Scheduler entry point. **The `zone` is pinned explicitly to `Asia/Kolkata`** — without it, a containerized JVM defaults to UTC and "19:00" would fire at the wrong wall-clock time. Disable via `nse.scheduler.enabled=false`.

### Deployment: why this must run continuously

Unlike the old Python script (invoked once daily by an external OS scheduler and then exiting), `BhavcopyScheduler`'s trigger lives *inside* the JVM process — the app must stay running 24/7 for the daily 19:00 IST job to fire at all. This is what [Dockerfile](markets-ref-data/Dockerfile) + [docker-compose.yml](markets-ref-data/docker-compose.yml) are for: `restart: unless-stopped` keeps the container alive across crashes/reboots (as long as Docker Desktop/daemon itself is running), and the `bhavcopy-data` named volume persists downloaded CSVs across container recreation. `TZ=Asia/Kolkata` is also set at the container level as a second layer on top of the explicit Spring `zone`.

### Testing conventions

JUnit 5 + Mockito + AssertJ; controller tests use `@WebMvcTest` + `MockMvc` + `@MockBean`. No live HTTP calls or real NSE data are used:
- **[client/NseHttpClientTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/client/NseHttpClientTest.java)** mocks `java.net.http.HttpClient` directly (it's injected, not constructed internally, specifically so this is mockable) and distinguishes the session-init call from the download call by inspecting the request URI in `thenAnswer`, since matching on `BodyHandlers.discarding()`/`ofByteArray()` by equality doesn't work (fresh lambda instances each call).
- **[service/BhavcopyServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java)** mocks `NseHttpClient` and uses `@TempDir` for `nse.data-dir` to assert real file writes without touching the repo.
- **[controller/BhavcopyControllerTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/controller/BhavcopyControllerTest.java)** covers the REST layer via `MockMvc`, plus direct unit tests of the package-private `BhavcopyController.lastWeekday()` static helper (Saturday/Sunday roll back to Friday, weekdays unchanged) rather than trying to mock `LocalDate.now()`.

### Known operational constraints (carried over from the original Python research)

- NSE aggressively blocks scraping; requests without proper headers return `401`/`403`.
- A session must be established by visiting `nseindia.com` first to obtain valid cookies before hitting archive file URLs directly — this is why `NseHttpClient` performs a warm-up `GET` in its constructor.
- Realistic browser `User-Agent`/`Accept`/`Accept-Language` headers (see `nse.headers` in `application.yml`) are required to bypass these restrictions.
