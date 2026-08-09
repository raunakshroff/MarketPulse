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
- **TimescaleDB Persistence:** Rows are upserted into an `equity_price` hypertable (keyed on `trade_date, symbol`) alongside an `equity_symbol` dimension table. Schema is managed by Flyway (`db/migration`) and applied automatically at startup.
- **REST API:** Exposes the download job and stored data over HTTP so other services can trigger/consume it.
- **Scheduled Daily Job:** Runs automatically every weekday at 19:00 IST via Spring's `@Scheduled`, with the timezone pinned explicitly so it's correct regardless of the host/container clock.
- **Containerized:** Ships with a `Dockerfile` and `docker-compose.yml` that bring up both the service and its TimescaleDB, running continuously (`restart: unless-stopped`), which is required for the in-process scheduler to actually fire day to day.
- **Unit Testing:** JUnit 5 + Mockito + MockMvc test suite covering the HTTP client, service, filtering, and REST layers without hitting the live NSE servers.

#### Directory Layout
```text
markets-ref-data/
├── src/main/java/com/marketpulse/refdata/
│   ├── RefDataApplication.java     # Spring Boot entry point
│   ├── client/                     # NseHttpClient (cookie session), NseHttpException
│   ├── config/                     # NseProperties (nse.* config), HttpClientConfig
│   ├── controller/                 # BhavcopyController (REST API)
│   ├── entity/                     # EquityPrice, EquityPriceId, EquitySymbol (JPA)
│   ├── model/                      # DownloadResult, EquityRecord
│   ├── repository/                 # Spring Data repos + custom batch-upsert impls
│   ├── scheduler/                  # BhavcopyScheduler (daily cron job)
│   └── service/                    # BhavcopyService, EquityCsvFilter
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                # Flyway schema (V1 = equity_price hypertable)
├── src/test/java/...                # Mirrors main/, JUnit 5 + Mockito + MockMvc
├── Dockerfile                       # Multi-stage build (Maven -> slim JRE)
├── docker-compose.yml               # markets-ref-data + timescaledb, restart: unless-stopped
└── pom.xml
```

#### Starting the Application (Docker — recommended)

The service needs a TimescaleDB to start at all (Flyway runs on boot and Hibernate validates the schema), so `docker compose` is the path that works from a clean machine. **Docker Desktop must be running** — no JDK or Maven needed on the host; the build happens inside the image.

1. **Start the stack** (first run pulls the TimescaleDB image and builds the app image, ~3-5 min; subsequent starts are seconds):
   ```powershell
   cd markets-ref-data
   docker compose up -d --build
   ```
   This starts two containers: `timescaledb` (Postgres 16 + TimescaleDB, published on `localhost:5432`) and `markets-ref-data` (published on `localhost:8081`). Compose waits for the database's `pg_isready` healthcheck before starting the app.

2. **Confirm both containers are healthy** (the app takes ~15s to report healthy after start):
   ```powershell
   docker compose ps
   ```
   Both rows should read `Up ... (healthy)`.

3. **Verify the app is serving:**
   ```powershell
   curl.exe http://localhost:8081/actuator/health
   ```
   Expected: `{"status":"UP"}`.

4. **Trigger a download** (omit `date` to use today, or the preceding Friday on a weekend):
   ```powershell
   curl.exe -X POST http://localhost:8081/api/v1/bhavcopy/download
   curl.exe -X POST "http://localhost:8081/api/v1/bhavcopy/download?date=2026-08-07"
   ```
   Expected on success: `{"date":"2026-08-07","status":"SUCCESS","location":"equity_price","rowCount":2416,...}`. A `404` with `status: NOT_FOUND` means a market holiday or the file isn't published yet — that's expected, not a failure.

5. **Read the stored rows back:**
   ```powershell
   curl.exe http://localhost:8081/api/v1/bhavcopy/2026-08-07
   ```
   Returns the equity-only rows as JSON, or `404` if that date was never downloaded.

6. **Follow logs / stop:**
   ```powershell
   docker compose logs -f markets-ref-data
   docker compose down          # stop; DB data survives in the timescaledb-data volume
   docker compose down -v       # stop and wipe the database volume
   ```

Leave the stack up: the 19:00 IST weekday scheduler lives inside the JVM, so it only fires while the container is running. `restart: unless-stopped` brings it back after a crash or reboot (as long as the Docker daemon is running). Persisted data lives in the `timescaledb-data` Docker volume and survives `docker compose down` and container rebuilds.

#### Running from source (alternative)

Requires **JDK 21** and Maven on the host. The database still has to be up — `application.yml` defaults to `jdbc:postgresql://localhost:5432/marketpulse`, which the composed `timescaledb` container serves.

```powershell
cd markets-ref-data
docker compose up -d timescaledb   # database only
mvn spring-boot:run                # app in the foreground on :8081
```

Other commands:
```powershell
mvn test                                    # full test suite (no DB or network needed)
mvn test -Dtest=BhavcopyServiceTest         # single test class
mvn package                                 # build the runnable jar
java -jar target/markets-ref-data-0.1.0-SNAPSHOT.jar
```

To point at a different database, override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. To stop the daily job from firing, set `nse.scheduler.enabled=false`.

#### REST API
- `POST /api/v1/bhavcopy/download?date=YYYY-MM-DD` — triggers a download. If `date` is omitted, defaults to today, or to the preceding Friday if today is a Saturday/Sunday. `200` on success, `404` if NSE has no file for that date (holiday / not yet published), `502` on an upstream failure.
- `GET /api/v1/bhavcopy/{date}` — returns the previously downloaded, equity-only rows for that date as JSON; `404` if nothing is stored for that date.
- `GET /actuator/health` — service health check.

## Research & Learnings
* **NSE Scraping Restrictions:** The NSE India servers have aggressive anti-scraping mechanisms. Direct requests without proper headers return `401 Unauthorized` or `403 Forbidden`.
* **Cookie Sessions:** It is required to first visit the base domain (`nseindia.com`) to establish a session and grab valid cookies before attempting to download archive files directly.
* **Header Configurations:** Supplying realistic browser `User-Agent`, `Accept`, and `Accept-Language` headers bypasses these restrictions effectively.
* **Timezone Pinning:** A container's system clock defaults to UTC, not IST. Cron schedules must pin their zone explicitly (`@Scheduled(zone = ...)`) rather than relying on the host/container default, otherwise a "19:00" trigger fires at the wrong wall-clock time.
