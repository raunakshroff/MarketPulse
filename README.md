# MarketPulse
Enterprise & Professional Level Markets Data Tracking and Analysis

## Project Structure
MarketPulse is a multi-module project.
Currently, it hosts the `markets-ref-data` module, which handles external data extraction and reference data management as a standalone microservice.

### Module: `markets-ref-data`
This module is responsible for systematically fetching market reference data, such as the daily NSE Bhavcopy files, and serving it over a REST API.

**Key Features:**
- **Automated Bhavcopy Downloads:** Fetches end-of-day (EOD) Bhavcopy CSVs from the National Stock Exchange (NSE), filtered down to Equity (`SERIES == EQ`) rows only.
- **Session & Header Management:** A cookie-aware `NseHttpClient` (built on `java.net.http.HttpClient`) manages session cookies and browser-like headers to bypass NSE's anti-scraping restrictions.
- **REST API:** Exposes the download job and stored data over HTTP so other services can trigger/consume it.
- **Scheduled Daily Job:** Runs automatically every weekday at 19:00 IST via Spring's `@Scheduled`, with the timezone pinned explicitly so it's correct regardless of the host/container clock.
- **Containerized:** Ships with a `Dockerfile` and `docker-compose.yml` so the service can run continuously (`restart: unless-stopped`), which is required for the in-process scheduler to actually fire day to day.
- **Unit Testing:** JUnit 5 + Mockito + MockMvc test suite covering the HTTP client, service, filtering, and REST layers without hitting the live NSE servers.

#### Directory Layout
```text
markets-ref-data/
├── src/main/java/com/marketpulse/refdata/
│   ├── RefDataApplication.java     # Spring Boot entry point
│   ├── client/                     # NseHttpClient (cookie session), NseHttpException
│   ├── config/                     # NseProperties (nse.* config), HttpClientConfig
│   ├── controller/                 # BhavcopyController (REST API)
│   ├── model/                      # DownloadResult, EquityRecord
│   ├── scheduler/                  # BhavcopyScheduler (daily cron job)
│   └── service/                    # BhavcopyService, EquityCsvFilter
├── src/main/resources/application.yml
├── src/test/java/...                # Mirrors main/, JUnit 5 + Mockito + MockMvc
├── Dockerfile                       # Multi-stage build (Maven -> slim JRE)
├── docker-compose.yml               # restart: unless-stopped + persistent data volume
└── pom.xml
```

#### Setup & Execution
1. **Prerequisites:** JDK 21 and Maven.
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
   This keeps the service running (and restarting on crash/reboot) so the 19:00 IST weekday scheduler actually fires. Downloaded CSVs persist in the `bhavcopy-data` Docker volume, mounted at `/app/data`.

#### REST API
- `POST /api/v1/bhavcopy/download?date=YYYY-MM-DD` — triggers a download. If `date` is omitted, defaults to today, or to the preceding Friday if today is a Saturday/Sunday.
- `GET /api/v1/bhavcopy/{date}` — returns the previously downloaded, equity-only rows for that date as JSON.
- `GET /actuator/health` — service health check.

## Research & Learnings
* **NSE Scraping Restrictions:** The NSE India servers have aggressive anti-scraping mechanisms. Direct requests without proper headers return `401 Unauthorized` or `403 Forbidden`.
* **Cookie Sessions:** It is required to first visit the base domain (`nseindia.com`) to establish a session and grab valid cookies before attempting to download archive files directly.
* **Header Configurations:** Supplying realistic browser `User-Agent`, `Accept`, and `Accept-Language` headers bypasses these restrictions effectively.
* **Timezone Pinning:** A container's system clock defaults to UTC, not IST. Cron schedules must pin their zone explicitly (`@Scheduled(zone = ...)`) rather than relying on the host/container default, otherwise a "19:00" trigger fires at the wrong wall-clock time.
