# MarketPulse
Enterprise & Professional Level Markets Data Tracking and Analysis

## Project Structure
MarketPulse is a multi-module project, split CQRS-style across the write and read sides of the same market data:
- **`markets-ref-data`** — the write/ingestion side. Fetches data from external sources (NSE, Yahoo Finance) and owns the TimescaleDB schema via Flyway.
- **`stock-discovery`** — the read/query side. A read-only microservice against the same database (no Flyway, `ddl-auto: none`) that exposes search and lookup APIs for the UI.
- **`markets-ui`** — the frontend. A React + Vite + TypeScript app (translated from the "Strata" design) that calls `stock-discovery` for real search/price/fundamentals data.

Future modules (Watchlist, Portfolio, Alerts, News, Identity — per the project's event-storming diagram) are expected to live as further sibling directories.

### Module: `markets-ref-data`
This module is responsible for systematically fetching market reference data — daily NSE Bhavcopy prices and, on demand, company fundamentals from Yahoo Finance — persisting it to TimescaleDB, and serving it over a REST API.

**Key Features:**
- **Automated Bhavcopy Downloads:** Fetches end-of-day (EOD) Bhavcopy CSVs from the National Stock Exchange (NSE), filtered down to Equity (`SERIES == EQ`) rows only.
- **TimescaleDB Persistence:** Daily prices land in a `equity_price` hypertable (partitioned by trade date) keyed by `(trade_date, symbol)`; re-running a download for the same date upserts rather than duplicates. A companion `equity_symbol` table tracks first/last-seen dates per ticker.
- **Bulk Historical Backfill:** A separate REST API queues an async job that walks a date range and downloads each missing weekday's Bhavcopy, skipping dates already stored so re-POSTing the same range resumes cheaply. NSE's archive only serves files back to 2019-10-01.
- **Company Fundamentals:** Fetches market cap, P/E ratio, sector/industry, and a company description from Yahoo Finance's unofficial API on demand, stored in `equity_fundamentals`.
- **Session & Header Management:** A cookie-aware `NseHttpClient` (built on `java.net.http.HttpClient`) manages session cookies and browser-like headers to bypass NSE's anti-scraping restrictions; `YahooFinanceClient` similarly handles Yahoo's cookie+crumb handshake.
- **REST API:** Exposes the download job, the bulk backfill jobs, and the fundamentals refresh, plus the stored data, over HTTP so other services can trigger/consume it.
- **Scheduled Daily Job:** Bhavcopy downloads run automatically every weekday at 19:00 IST via Spring's `@Scheduled`, with the timezone pinned explicitly so it's correct regardless of the host/container clock. (Backfill and fundamentals refresh are on-demand only — no schedule.)
- **Containerized:** Ships with a `Dockerfile`, and the repo-root `docker-compose.yml` brings it up alongside TimescaleDB running continuously (`restart: unless-stopped`), which is required for the in-process scheduler to actually fire day to day.
- **Unit Testing:** JUnit 5 + Mockito + MockMvc test suite covering both HTTP clients, all services, filtering, and the REST layers without hitting the live NSE/Yahoo servers.

#### Directory Layout
```text
markets-ref-data/
├── src/main/java/com/marketpulse/refdata/
│   ├── RefDataApplication.java     # Spring Boot entry point
│   ├── client/                     # NseHttpClient, YahooFinanceClient (+ their exceptions)
│   ├── config/                     # NseProperties, YahooFinanceProperties, HttpClientConfig, AsyncConfig
│   ├── controller/                 # BhavcopyController, BackfillController, FundamentalsController
│   ├── entity/                     # EquityPrice, EquitySymbol, EquityFundamentals, BackfillJob(+Date)
│   ├── model/                      # DownloadResult, EquityRecord, FundamentalsResult/View, Backfill*Status
│   ├── repository/                 # JPA repos + JdbcTemplate-based upsert impls
│   ├── scheduler/                  # BhavcopyScheduler (daily cron job)
│   └── service/                    # BhavcopyService, BackfillService, EquityCsvFilter, FundamentalsService
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/               # Flyway: V1 hypertable, V2 fundamentals, V3 backfill jobs, V4 seen-date repair
├── src/test/java/...                # Mirrors main/, JUnit 5 + Mockito + MockMvc
├── Dockerfile                       # Multi-stage build (Maven -> slim JRE)
└── pom.xml
```

#### Setup & Execution
1. **Prerequisites:** JDK 25, Maven, and Docker (for TimescaleDB).
2. **Run the test suite:**
   ```powershell
   cd markets-ref-data
   mvn test
   ```
3. **Run locally (foreground):** the service needs a reachable TimescaleDB to start at all (Flyway migrates on boot and Hibernate validates the schema), so bring the database up first:
   ```powershell
   docker compose -f ../docker-compose.yml up -d timescaledb
   mvn spring-boot:run
   ```
   To point at a different database, override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. To stop the daily job firing, set `nse.scheduler.enabled=false`.
4. **Run continuously via Docker (recommended):** see [Running everything together](#running-everything-together) below — `docker-compose.yml` lives at the repo root and brings up all four services (this module included) together.

#### REST API
- `POST /api/v1/bhavcopy/download?date=YYYY-MM-DD` — triggers a download. If `date` is omitted, defaults to today, or to the preceding Friday if today is a Saturday/Sunday. `200` on success, `404` if NSE has no file for that date (holiday / not yet published), `502` on an upstream failure.
- `GET /api/v1/bhavcopy/{date}` — returns the previously downloaded, equity-only rows for that date as JSON; `404` if nothing is stored for that date.
- `POST /api/v1/bhavcopy/backfill?from=YYYY-MM-DD&to=YYYY-MM-DD&force=false` — queues a bulk backfill over `[from, to]` and returns immediately; the walk runs in the background. `202` with the new job (id + progress), `400` if `from` is after `to`, `to` is in the future, or `from` is before 2019-10-01 (the earliest date NSE's archive serves), `409` if a backfill job is already pending or running. Dates already stored in `equity_price` are skipped by default, so re-POSTing the same range only fetches what is still missing; pass `force=true` to re-download everything in range regardless.
- `GET /api/v1/bhavcopy/backfill/{jobId}` — job status and a per-date breakdown (success/failed/skipped/not-found) for one backfill job; `404` if the job id is unknown.
- `GET /api/v1/bhavcopy/backfill` — the 50 most recent backfill jobs, newest first.
- `POST /api/v1/fundamentals/refresh?symbols=RELIANCE,TCS` — fetches and stores fundamentals for one or more symbols (comma-separated) from Yahoo Finance; returns a per-symbol SUCCESS/NOT_FOUND/FAILURE result so one bad symbol doesn't fail the rest.
- `GET /api/v1/fundamentals/{symbol}` — returns previously fetched fundamentals for a symbol, 404 if never refreshed.
- `GET /actuator/health` — service health check.
- **`GET /swagger-ui.html`** — interactive Swagger UI ([localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)); raw OpenAPI 3 document at `/v3/api-docs`.

### Module: `stock-discovery`
The read-only query side of the same market data. Connects to the same TimescaleDB database as `markets-ref-data` (no Flyway, `ddl-auto: none` — this service never migrates or writes to the schema) and exposes the search/lookup APIs the UI needs.

**Key Features:**
- **Symbol Search:** `ILIKE` search across ticker symbol and company name (from `equity_fundamentals`, when present), backed by a `JdbcTemplate` query rather than JPA for the `LEFT JOIN`.
- **Price History:** Full OHLCV time series for a symbol from the `equity_price` hypertable.
- **Fundamentals Lookup:** Market cap, P/E, sector, industry, and description for a symbol, when previously fetched by `markets-ref-data`.
- **CORS:** Configured (`app.cors.allowed-origins`) to allow the browser-based `markets-ui` to call it directly.

#### REST API
- `GET /api/v1/stocks/search?q=RELI&limit=20` — symbol/company-name search.
- `GET /api/v1/stocks/{symbol}/history` — full price history for a symbol, 404 if the symbol has no data.
- `GET /api/v1/stocks/{symbol}/fundamentals` — fundamentals for a symbol, 404 if never fetched.
- `GET /actuator/health` — service health check.
- **`GET /swagger-ui.html`** — interactive Swagger UI ([localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)); raw OpenAPI 3 document at `/v3/api-docs`.

#### Directory Layout
```text
stock-discovery/
├── src/main/java/com/marketpulse/stockdiscovery/
│   ├── StockDiscoveryApplication.java
│   ├── config/                      # CorsConfig
│   ├── controller/                  # StockDiscoveryController (REST API)
│   ├── entity/                      # EquitySymbol, EquityPrice, EquityFundamentals (read-mapped)
│   ├── model/                       # EquitySearchResult, EquityRecord, FundamentalsView
│   ├── repository/                  # JdbcTemplate search + JPA read repos
│   └── service/                     # StockDiscoveryService
├── src/main/resources/application.yml   # no Flyway - schema owned by markets-ref-data
├── src/test/java/...                # JUnit 5 + Mockito + MockMvc, mocked repositories
├── Dockerfile
└── pom.xml
```

#### Setup & Execution
```powershell
cd stock-discovery
mvn test
mvn spring-boot:run   # needs TimescaleDB reachable at localhost:5432
```

### Module: `markets-ui`
A React + Vite + TypeScript frontend, translated from the "Strata" design (originally a Claude Design canvas project, `Strata.dc.html` + `support.js` — a proprietary format that only renders inside Claude Design's own editor, so the design was reimplemented as real React/CSS rather than shipped directly).

**Real vs. mock data:** Search and Stock Detail are wired to real `stock-discovery` data. Dashboard, Watchlist, Markets, Portfolio, Market Wire, and Alerts still run on the design's own placeholder data generator (`src/lib/mockData.ts`), clearly marked in-UI with a "demo data" badge — those screens need backing services (Watchlist, Portfolio, Alerts, News, Market Data) that don't exist in this system yet.

#### Directory Layout
```text
markets-ui/
├── src/
│   ├── api/client.ts                # fetch helpers calling stock-discovery
│   ├── components/                  # Layout (header/search/sidebar), Sparkline, MockDataNote
│   ├── lib/
│   │   ├── chart.ts                 # ported SVG chart math (line/area/candles)
│   │   └── mockData.ts              # Strata's placeholder data generator (non-real screens only)
│   ├── pages/                       # Dashboard, Watchlist, Markets, Portfolio, News, Alerts, StockDetail
│   ├── theme.css                    # ported design tokens (dark/light palettes, fonts)
│   ├── ThemeContext.tsx
│   ├── types.ts                     # mirrors stock-discovery's DTOs
│   └── App.tsx                      # React Router routes
├── Dockerfile                       # multi-stage build (Node -> nginx:alpine)
├── nginx.conf                       # SPA fallback for client-side routing
└── .env                             # VITE_API_BASE_URL
```

#### Setup & Execution
```powershell
cd markets-ui
npm install
npm run dev     # needs stock-discovery reachable at the URL in .env (VITE_API_BASE_URL)
npm run build   # production build, output in dist/
```

## Running everything together
From the repo root, `docker-compose.yml` brings up all four services — `timescaledb`, `markets-ref-data` (port 8081), `stock-discovery` (port 8082), and `markets-ui` (port 3000, served via nginx) — wired together with the correct dependency order (`markets-ref-data`/`stock-discovery` wait on TimescaleDB's healthcheck; `markets-ui` waits on `stock-discovery`):
```powershell
docker compose up -d --build
```
`restart: unless-stopped` on every service keeps them alive across crashes/reboots (required for `markets-ref-data`'s in-process 19:00 IST scheduler to actually fire day to day), and the `timescaledb-data` named volume persists price/fundamentals data across container recreation.

Once up:
| | |
|---|---|
| UI | [localhost:3000](http://localhost:3000) |
| `markets-ref-data` Swagger | [localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) |
| `stock-discovery` Swagger | [localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html) |

## Research & Learnings
* **NSE Scraping Restrictions:** The NSE India servers have aggressive anti-scraping mechanisms. Direct requests without proper headers return `401 Unauthorized` or `403 Forbidden`.
* **Cookie Sessions:** It is required to first visit the base domain (`nseindia.com`) to establish a session and grab valid cookies before attempting to download archive files directly.
* **Header Configurations:** Supplying realistic browser `User-Agent`, `Accept`, and `Accept-Language` headers bypasses these restrictions effectively.
* **Timezone Pinning:** A container's system clock defaults to UTC, not IST. Cron schedules must pin their zone explicitly (`@Scheduled(zone = ...)`) rather than relying on the host/container default, otherwise a "19:00" trigger fires at the wrong wall-clock time.
* **Yahoo Finance's Crumb Requirement:** A plain request to Yahoo's unofficial `quoteSummary` endpoint 401s with "Invalid Crumb" — it now requires warming up a cookie, exchanging it for a crumb token, then including both on the actual request. This is a session-lifetime handshake (done once at startup), not per-request.
* **Yahoo Finance Rate Limiting:** The same unofficial API throttles aggressively under repeated testing (`429`, with a plain-text `"Edge: Too Many Requests"` body rather than JSON) — worth handling defensively (check status before parsing) and pacing requests if refreshing many symbols.
* **NSE vs. Fundamentals:** NSE's Bhavcopy/quote APIs carry price and trading-activity data only — no market cap, P/E, sector, or company description. Fundamentals require a separate source entirely (Yahoo Finance here).
* **Spring Boot 4 is a major upgrade, not a patch bump:** moving from Spring Boot 3.3.4 to 4.1.1 (Java 21 → 25) pulled in real breaking changes — a restructured Flyway starter, self-managed Testcontainers versions, a Jackson 2→3 default switch, and `@WebMvcTest` now hard-requiring a Spring Security class on the classpath even for apps with no Security dependency. See `CLAUDE.md` for the specifics if touching dependencies.
