# MarketPulse
Enterprise & Professional Level Markets Data Tracking and Analysis

## Project Structure
MarketPulse is a multi-module project.
Currently, it hosts the `markets-ref-data` module, which handles external data extraction and reference data management as a standalone microservice.

### Module: `markets-ref-data`
This module is responsible for systematically fetching market reference data — daily NSE Bhavcopy prices and, on demand, company fundamentals from Yahoo Finance — persisting it to TimescaleDB, and serving it over a REST API.

**Key Features:**
- **Automated Bhavcopy Downloads:** Fetches end-of-day (EOD) Bhavcopy CSVs from the National Stock Exchange (NSE), filtered down to Equity (`SERIES == EQ`) rows only.
- **TimescaleDB Persistence:** Daily prices land in a `equity_price` hypertable (partitioned by trade date) keyed by `(trade_date, symbol)`; re-running a download for the same date upserts rather than duplicates. A companion `equity_symbol` table tracks first/last-seen dates per ticker.
- **Company Fundamentals:** Fetches market cap, P/E ratio, sector/industry, and a company description from Yahoo Finance's unofficial API on demand, stored in `equity_fundamentals`.
- **Session & Header Management:** A cookie-aware `NseHttpClient` (built on `java.net.http.HttpClient`) manages session cookies and browser-like headers to bypass NSE's anti-scraping restrictions; `YahooFinanceClient` similarly handles Yahoo's cookie+crumb handshake.
- **REST API:** Exposes both the download job and fundamentals refresh, plus the stored data, over HTTP so other services can trigger/consume it.
- **Scheduled Daily Job:** Bhavcopy downloads run automatically every weekday at 19:00 IST via Spring's `@Scheduled`, with the timezone pinned explicitly so it's correct regardless of the host/container clock. (Fundamentals refresh is on-demand only — no schedule.)
- **Containerized:** Ships with a `Dockerfile` and `docker-compose.yml` (app + TimescaleDB) so the service can run continuously (`restart: unless-stopped`), which is required for the in-process scheduler to actually fire day to day.
- **Unit Testing:** JUnit 5 + Mockito + MockMvc test suite covering both HTTP clients, both services, filtering, and the REST layers without hitting the live NSE/Yahoo servers.

#### Directory Layout
```text
markets-ref-data/
├── src/main/java/com/marketpulse/refdata/
│   ├── RefDataApplication.java     # Spring Boot entry point
│   ├── client/                     # NseHttpClient, YahooFinanceClient (+ their exceptions)
│   ├── config/                     # NseProperties, YahooFinanceProperties, HttpClientConfig
│   ├── controller/                 # BhavcopyController, FundamentalsController (REST API)
│   ├── entity/                     # EquityPrice, EquitySymbol, EquityFundamentals (JPA)
│   ├── model/                      # DownloadResult, EquityRecord, FundamentalsResult, FundamentalsView
│   ├── repository/                 # JPA repos + JdbcTemplate-based upsert impls
│   ├── scheduler/                  # BhavcopyScheduler (daily cron job)
│   └── service/                    # BhavcopyService, EquityCsvFilter, FundamentalsService
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/               # Flyway: V1 equity_price hypertable, V2 equity_fundamentals
├── src/test/java/...                # Mirrors main/, JUnit 5 + Mockito + MockMvc
├── Dockerfile                       # Multi-stage build (Maven -> slim JRE)
├── docker-compose.yml               # app + timescaledb, restart: unless-stopped
└── pom.xml
```

#### Setup & Execution
1. **Prerequisites:** JDK 21, Maven, and Docker (for TimescaleDB).
2. **Run the test suite:**
   ```powershell
   cd markets-ref-data
   mvn test
   ```
3. **Run locally (foreground):**
   ```powershell
   mvn spring-boot:run
   ```
4. **Run continuously via Docker (recommended):**
   ```powershell
   cd markets-ref-data
   docker compose up -d --build
   ```
   This brings up `timescaledb` first (waiting for it to report healthy) then the app, which applies Flyway migrations on boot. `restart: unless-stopped` keeps both running across crashes/reboots so the 19:00 IST weekday scheduler actually fires. Price data persists in the `timescaledb-data` Docker volume.

#### REST API
- `POST /api/v1/bhavcopy/download?date=YYYY-MM-DD` — triggers a download. If `date` is omitted, defaults to today, or to the preceding Friday if today is a Saturday/Sunday.
- `GET /api/v1/bhavcopy/{date}` — returns the previously downloaded, equity-only rows for that date as JSON.
- `POST /api/v1/fundamentals/refresh?symbols=RELIANCE,TCS` — fetches and stores fundamentals for one or more symbols (comma-separated) from Yahoo Finance; returns a per-symbol SUCCESS/NOT_FOUND/FAILURE result so one bad symbol doesn't fail the rest.
- `GET /api/v1/fundamentals/{symbol}` — returns previously fetched fundamentals for a symbol, 404 if never refreshed.
- `GET /actuator/health` — service health check.

## Research & Learnings
* **NSE Scraping Restrictions:** The NSE India servers have aggressive anti-scraping mechanisms. Direct requests without proper headers return `401 Unauthorized` or `403 Forbidden`.
* **Cookie Sessions:** It is required to first visit the base domain (`nseindia.com`) to establish a session and grab valid cookies before attempting to download archive files directly.
* **Header Configurations:** Supplying realistic browser `User-Agent`, `Accept`, and `Accept-Language` headers bypasses these restrictions effectively.
* **Timezone Pinning:** A container's system clock defaults to UTC, not IST. Cron schedules must pin their zone explicitly (`@Scheduled(zone = ...)`) rather than relying on the host/container default, otherwise a "19:00" trigger fires at the wrong wall-clock time.
* **Yahoo Finance's Crumb Requirement:** A plain request to Yahoo's unofficial `quoteSummary` endpoint 401s with "Invalid Crumb" — it now requires warming up a cookie, exchanging it for a crumb token, then including both on the actual request. This is a session-lifetime handshake (done once at startup), not per-request.
* **Yahoo Finance Rate Limiting:** The same unofficial API throttles aggressively under repeated testing (`429`, with a plain-text `"Edge: Too Many Requests"` body rather than JSON) — worth handling defensively (check status before parsing) and pacing requests if refreshing many symbols.
* **NSE vs. Fundamentals:** NSE's Bhavcopy/quote APIs carry price and trading-activity data only — no market cap, P/E, sector, or company description. Fundamentals require a separate source entirely (Yahoo Finance here).
