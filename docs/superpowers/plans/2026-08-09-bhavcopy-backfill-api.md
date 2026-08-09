# Bhavcopy Date-Range Backfill API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an async, resumable API that backfills NSE Bhavcopy history over a date range, driven by a persisted job table, fetching only dates not already stored.

**Architecture:** A new `BackfillService` bean walks the weekdays in a range and calls the existing `BhavcopyService.downloadBhavcopy(date)` once per date. Because `BackfillService` is a *separate* bean, Spring's `@Transactional` proxy on `downloadBhavcopy` genuinely applies, so each date commits independently and a late failure cannot roll back earlier days. Execution is `@Async` on a single-thread executor; progress and per-date outcomes persist in two new plain tables so a job survives restarts and can be resumed by re-POSTing the same range.

**Tech Stack:** Java 21, Spring Boot (Web, Data JPA, Actuator), Flyway, TimescaleDB/PostgreSQL, Apache Commons CSV, JUnit 5 + Mockito + AssertJ, Testcontainers.

**Spec:** [docs/superpowers/specs/2026-08-09-bhavcopy-backfill-range-api-design.md](../specs/2026-08-09-bhavcopy-backfill-range-api-design.md)

## Global Constraints

- All commands run from the `markets-ref-data/` directory. JDK 21 + Maven.
- Package root is `com.marketpulse.refdata`. 4-space indent, no wildcard imports.
- **Never edit `V1__create_equity_price_hypertable.sql`.** Schema changes go in a new `V2__*.sql`.
- `spring.jpa.hibernate.ddl-auto: validate` — entity mappings MUST match the SQL exactly or the app will not boot.
- There is **no** `spring-boot-starter-validation` dependency. Do all request validation with plain Java in the controller. Do not add the dependency.
- Tests use JUnit 5 + Mockito + AssertJ. Controller tests use `@WebMvcTest` + `MockMvc` + `@MockBean` (match the existing style in `BhavcopyControllerTest`, which uses `@MockBean` — do not migrate to `@MockitoBean`).
- `mvn test` does NOT run `*IT` classes (no failsafe plugin; Surefire's default includes don't match `*IT`). Integration tests must be invoked by name and need a running Docker daemon.
- No live HTTP calls or real NSE data in any test.
- Every commit message ends with the trailer: `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`
- Full unit suite before this work: **14 tests**. It must stay green at every commit.

## Deviation from the spec (approved)

The spec's `backfill_job` DDL omits a column for the `force` flag, but the job runs
asynchronously — the worker thread reads the job back from the database and must know
whether to bypass the skip sets. **`force BOOLEAN NOT NULL DEFAULT FALSE` is added to
`backfill_job`** in Task 3. It is also useful as an audit record of how a job was started.

---

## File Structure

**Task 1 — `DATE1` guard (standalone bug fix)**
- Modify: `src/main/java/com/marketpulse/refdata/service/BhavcopyService.java`
- Modify: `src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java`
- Modify: `src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java`

**Task 2 — Backfill configuration**
- Modify: `src/main/java/com/marketpulse/refdata/config/NseProperties.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/marketpulse/refdata/config/NsePropertiesTest.java`

**Task 3 — Schema, entities, repositories**
- Create: `src/main/resources/db/migration/V2__create_backfill_job.sql`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillJobStatus.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillDateStatus.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJob.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJobDate.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJobDateId.java`
- Create: `src/main/java/com/marketpulse/refdata/repository/BackfillJobRepository.java`
- Create: `src/main/java/com/marketpulse/refdata/repository/BackfillJobDateRepository.java`
- Modify: `src/main/java/com/marketpulse/refdata/repository/EquityPriceRepository.java`

**Task 4 — `BackfillService` walk (synchronous core)**
- Create: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java`

**Task 5 — Async wiring + startup reconciliation**
- Create: `src/main/java/com/marketpulse/refdata/config/AsyncConfig.java`
- Create: `src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java`
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillStartupReconcilerTest.java`
- Modify: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`

**Task 6 — REST API**
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillJobResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillDateResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillErrorResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/controller/BackfillController.java`
- Create: `src/test/java/com/marketpulse/refdata/controller/BackfillControllerTest.java`
- Modify: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`

**Task 7 — Integration test**
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java`

**Task 8 — Documentation**
- Modify: `CLAUDE.md` (repo root)

---

### Task 1: `DATE1` validation guard

Closes the hole found during research: `sec_bhavdata_full_30092019.csv` returns HTTP 200
but contains rows dated `27-Jun-2019`. `parseToEntities` stamps `trade_date` from the
*requested* date and never reads `DATE1`, so that file would silently persist June prices
under a September trade date.

**Two existing tests currently rely on the mismatch** and will fail once the guard lands —
both use a fixture dated `10-Jul-2026` while requesting `2025-11-14`. Fixing them is part
of this task, not an accident to work around.

**Files:**
- Modify: `src/main/java/com/marketpulse/refdata/service/BhavcopyService.java`
- Modify: `src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java:52-69`
- Modify: `src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java:55,72`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `DownloadResult.Status.FAILURE` for a date/content mismatch. Task 4 relies on
  `FAILURE` being what a mismatch looks like, so the backfill circuit breaker counts it.

**Behavioral note:** the guard runs on the *filtered* CSV, which `EquityCsvFilter` has
already trimmed, so `record.get("DATE1")` is clean (`10-Jul-2026`, no padding). If the
filtered CSV has zero EQ rows there is no `DATE1` to check; that case stays `SUCCESS` with
`rowCount` 0, unchanged from today.

- [ ] **Step 1: Realign the two existing fixtures to a matching date**

In `BhavcopyServiceTest.downloadsFiltersAndPersistsEquityRowsOnly`, change the requested
date and URL to match the fixture's `10-Jul-2026`:

```java
    @Test
    void downloadsFiltersAndPersistsEquityRowsOnly() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 10);
        when(nseHttpClient.downloadFile(ARCHIVE_URL + "sec_bhavdata_full_10072026.csv")).thenReturn(RAW_CSV);
```

In `BhavcopyServiceIT`, change line 55 and the assertion on line 72 the same way:

```java
        LocalDate date = LocalDate.of(2026, 7, 10);
```
```java
        assertThat(records.get(0).date()).isEqualTo("10-Jul-2026");
```

- [ ] **Step 2: Run the unit suite to confirm it is still green**

Run: `mvn test`
Expected: PASS, 14 tests. (The realignment alone changes no behavior.)

- [ ] **Step 3: Write the failing tests for the guard**

Add to `BhavcopyServiceTest`:

```java
    private static final byte[] MISMATCHED_CSV = ("""
            SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
            20MICRONS, EQ, 27-Jun-2019, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
            """).getBytes(StandardCharsets.UTF_8);

    @Test
    void rejectsCsvWhoseContentDateDisagreesWithRequestedDate() throws Exception {
        LocalDate requested = LocalDate.of(2019, 9, 30);
        when(nseHttpClient.downloadFile(anyString())).thenReturn(MISMATCHED_CSV);

        DownloadResult result = service.downloadBhavcopy(requested);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.FAILURE);
        assertThat(result.message()).contains("2019-09-30").contains("2019-06-27");
        verify(equityPriceRepository, never()).upsertAll(any());
        verify(equitySymbolRepository, never()).upsertAll(any(), any());
    }

    @Test
    void acceptsCsvWhoseContentDateMatchesRequestedDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 10);
        when(nseHttpClient.downloadFile(anyString())).thenReturn(RAW_CSV);

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(1);
    }
```

Add these imports to the test file:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
```

- [ ] **Step 4: Run the new tests to verify they fail**

Run: `mvn test -Dtest=BhavcopyServiceTest#rejectsCsvWhoseContentDateDisagreesWithRequestedDate`
Expected: FAIL — status is `SUCCESS`, not `FAILURE` (no guard exists yet).

- [ ] **Step 5: Implement the guard**

In `BhavcopyService`, add a private exception type at the bottom of the class:

```java
    /** Raised when the downloaded CSV's DATE1 column disagrees with the date we asked for. */
    private static class DateMismatchException extends RuntimeException {
        DateMismatchException(String message) {
            super(message);
        }
    }
```

In `parseToEntities`, validate every row's `DATE1` against the requested date. Reuse the
existing `WIRE_DATE_FORMAT` (already `dd-MMM-yyyy` with `Locale.ENGLISH`) rather than
declaring a second identical formatter. Inside the `for (CSVRecord record : parser)` loop,
before the `rows.add(...)` call:

```java
                LocalDate contentDate = LocalDate.parse(record.get("DATE1"), WIRE_DATE_FORMAT);
                if (!contentDate.equals(date)) {
                    throw new DateMismatchException(
                            "Bhavcopy content date mismatch: requested " + date
                                    + " but the downloaded file contains rows dated " + contentDate
                                    + ". Refusing to persist.");
                }
```

Then add a catch clause in `downloadBhavcopy`, immediately before the existing
`catch (IOException e)` block:

```java
        } catch (DateMismatchException e) {
            log.error("{}", e.getMessage());
            return DownloadResult.failure(date, e.getMessage());
```

- [ ] **Step 6: Run the full suite**

Run: `mvn test`
Expected: PASS, 16 tests (14 existing + 2 new).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/marketpulse/refdata/service/BhavcopyService.java src/test/java/com/marketpulse/refdata/service/BhavcopyServiceTest.java src/test/java/com/marketpulse/refdata/service/BhavcopyServiceIT.java
git commit -m "fix: reject Bhavcopy CSV whose DATE1 disagrees with the requested date" -m "NSE serves sec_bhavdata_full_30092019.csv with HTTP 200 but rows dated 27-Jun-2019. parseToEntities stamped trade_date from the requested date and never read DATE1, so that file would silently persist June prices under a September trade date." -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Backfill configuration properties

**Files:**
- Modify: `src/main/java/com/marketpulse/refdata/config/NseProperties.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/marketpulse/refdata/config/NsePropertiesTest.java`

**Interfaces:**
- Produces: `NseProperties.getBackfill()` returning a `NseProperties.Backfill` with
  `long getDelayMs()`, `int getMaxConsecutiveFailures()`, `LocalDate getEarliestDate()`
  and matching setters. Tasks 4 and 6 both depend on these exact names.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/marketpulse/refdata/config/NsePropertiesTest.java`:

```java
package com.marketpulse.refdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NsePropertiesTest {

    @Test
    void backfillDefaultsMatchTheValidatedNsePacing() {
        NseProperties properties = new NseProperties();

        assertThat(properties.getBackfill().getDelayMs()).isEqualTo(2000L);
        assertThat(properties.getBackfill().getMaxConsecutiveFailures()).isEqualTo(10);
        assertThat(properties.getBackfill().getEarliestDate()).isEqualTo(LocalDate.of(2019, 10, 1));
    }

    @Test
    void backfillSettingsAreOverridable() {
        NseProperties properties = new NseProperties();

        properties.getBackfill().setDelayMs(0L);
        properties.getBackfill().setMaxConsecutiveFailures(3);
        properties.getBackfill().setEarliestDate(LocalDate.of(2020, 1, 1));

        assertThat(properties.getBackfill().getDelayMs()).isZero();
        assertThat(properties.getBackfill().getMaxConsecutiveFailures()).isEqualTo(3);
        assertThat(properties.getBackfill().getEarliestDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=NsePropertiesTest`
Expected: FAIL — compilation error, `getBackfill()` is undefined.

- [ ] **Step 3: Add the nested `Backfill` class**

In `NseProperties`, add the field and accessor alongside the existing `scheduler` ones:

```java
    private Backfill backfill = new Backfill();

    public Backfill getBackfill() {
        return backfill;
    }

    public void setBackfill(Backfill backfill) {
        this.backfill = backfill;
    }
```

And the nested class, after the existing `Scheduler` class:

```java
    public static class Backfill {

        /** Pause between per-date downloads. 2s is the pace validated against NSE without rate-limiting. */
        private long delayMs = 2000L;

        /** Consecutive FAILURE outcomes that abort a job, so a blocked run stops instead of grinding on. */
        private int maxConsecutiveFailures = 10;

        /**
         * Earliest date the NSE archive serves a trustworthy sec_bhavdata_full file.
         * 2019-09-30 returns HTTP 200 but contains rows dated 27-Jun-2019, so the usable floor is the 1st.
         */
        private LocalDate earliestDate = LocalDate.of(2019, 10, 1);

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getMaxConsecutiveFailures() {
            return maxConsecutiveFailures;
        }

        public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
        }

        public LocalDate getEarliestDate() {
            return earliestDate;
        }

        public void setEarliestDate(LocalDate earliestDate) {
            this.earliestDate = earliestDate;
        }
    }
```

Add the import `java.time.LocalDate` to `NseProperties`.

- [ ] **Step 4: Add the YAML block**

In `application.yml`, nested under the existing `nse:` key, after the `scheduler:` block:

```yaml
  backfill:
    # Pause between per-date downloads during a bulk range pull.
    delay-ms: 2000
    # Abort a job after this many consecutive failures rather than burning an hour on errors.
    max-consecutive-failures: 10
    # NSE serves no usable sec_bhavdata_full file before this date.
    earliest-date: 2019-10-01
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test`
Expected: PASS, 18 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marketpulse/refdata/config/NseProperties.java src/main/resources/application.yml src/test/java/com/marketpulse/refdata/config/NsePropertiesTest.java
git commit -m "feat: add nse.backfill configuration properties" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Schema, entities, repositories

**Files:**
- Create: `src/main/resources/db/migration/V2__create_backfill_job.sql`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillJobStatus.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillDateStatus.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJob.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJobDate.java`
- Create: `src/main/java/com/marketpulse/refdata/entity/BackfillJobDateId.java`
- Create: `src/main/java/com/marketpulse/refdata/repository/BackfillJobRepository.java`
- Create: `src/main/java/com/marketpulse/refdata/repository/BackfillJobDateRepository.java`
- Modify: `src/main/java/com/marketpulse/refdata/repository/EquityPriceRepository.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, relied on by Tasks 4–7:
  - `BackfillJob(LocalDate fromDate, LocalDate toDate, boolean force, int totalDates)` — constructs a `PENDING` job with a fresh `UUID` and `createdAt`.
  - `BackfillJob`: `getId()`, `getFromDate()`, `getToDate()`, `isForce()`, `getStatus()`, `getTotalDates()`, `getProcessedDates()`, `getCreatedAt()`, `getStartedAt()`, `getFinishedAt()`, `getMessage()`, plus `markRunning()`, `markCompleted()`, `markFailed(String)`, `markInterrupted(String)`, `incrementProcessed()`.
  - `BackfillJobDate(UUID jobId, LocalDate tradeDate, BackfillDateStatus status, long rowCount, String message)` — sets `attemptedAt` to now.
  - `BackfillJobRepository.findFirstByStatusInOrderByCreatedAtAsc(Collection<BackfillJobStatus>)`, `findByStatusIn(Collection<BackfillJobStatus>)`, `findTop50ByOrderByCreatedAtDesc()`.
  - `BackfillJobDateRepository.findByJobIdOrderByTradeDateAsc(UUID)`, `findKnownNonTradingDates(LocalDate, LocalDate)`.
  - `EquityPriceRepository.findDistinctTradeDatesBetween(LocalDate, LocalDate)`.

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V2__create_backfill_job.sql`. These are plain
tables, **not** hypertables — a 5-year job writes ~1,305 metadata rows, and they are
queried by job id, not by time range.

```sql
CREATE TABLE IF NOT EXISTS backfill_job (
    id               UUID         NOT NULL,
    from_date        DATE         NOT NULL,
    to_date          DATE         NOT NULL,
    force            BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL,
    total_dates      INTEGER      NOT NULL,
    processed_dates  INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    message          TEXT,
    CONSTRAINT pk_backfill_job PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS backfill_job_date (
    job_id       UUID        NOT NULL REFERENCES backfill_job (id),
    trade_date   DATE        NOT NULL,
    status       VARCHAR(20) NOT NULL,
    row_count    BIGINT      NOT NULL DEFAULT 0,
    message      TEXT,
    attempted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_backfill_job_date PRIMARY KEY (job_id, trade_date)
);

CREATE INDEX IF NOT EXISTS idx_backfill_job_date_status
    ON backfill_job_date (status, trade_date);

CREATE INDEX IF NOT EXISTS idx_backfill_job_created_at
    ON backfill_job (created_at DESC);
```

- [ ] **Step 2: Create the two status enums**

`src/main/java/com/marketpulse/refdata/model/BackfillJobStatus.java`:

```java
package com.marketpulse.refdata.model;

/** Lifecycle of one backfill job. */
public enum BackfillJobStatus {
    /** Created and queued, not yet picked up by the worker thread. */
    PENDING,
    /** The worker is walking the range. */
    RUNNING,
    /** The walk finished; per-date failures may still be recorded. */
    COMPLETED,
    /** Aborted by the consecutive-failure circuit breaker. */
    FAILED,
    /** The owning JVM died mid-run; reconciled at startup. */
    INTERRUPTED
}
```

`src/main/java/com/marketpulse/refdata/model/BackfillDateStatus.java`:

```java
package com.marketpulse.refdata.model;

/** Outcome of one date within a backfill job. */
public enum BackfillDateStatus {
    /** Downloaded and persisted. */
    SUCCESS,
    /** NSE published no file: market holiday, or not yet available. Not a failure. */
    NOT_FOUND,
    /** Download or parse error, including a DATE1 content mismatch. */
    FAILED,
    /** Already persisted, or already known to be a non-trading day. Never fetched. */
    SKIPPED
}
```

- [ ] **Step 3: Create the `BackfillJob` entity**

`src/main/java/com/marketpulse/refdata/entity/BackfillJob.java`:

```java
package com.marketpulse.refdata.entity;

import com.marketpulse.refdata.model.BackfillJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One bulk backfill run over a date range, tracked in the {@code backfill_job} table. */
@Entity
@Table(name = "backfill_job")
public class BackfillJob {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "force", nullable = false)
    private boolean force;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackfillJobStatus status;

    @Column(name = "total_dates", nullable = false)
    private int totalDates;

    @Column(name = "processed_dates", nullable = false)
    private int processedDates;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "message")
    private String message;

    protected BackfillJob() {
        // for JPA
    }

    public BackfillJob(LocalDate fromDate, LocalDate toDate, boolean force, int totalDates) {
        this.id = UUID.randomUUID();
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.force = force;
        this.status = BackfillJobStatus.PENDING;
        this.totalDates = totalDates;
        this.processedDates = 0;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = BackfillJobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = BackfillJobStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = BackfillJobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.message = message;
    }

    public void markInterrupted(String message) {
        this.status = BackfillJobStatus.INTERRUPTED;
        this.finishedAt = Instant.now();
        this.message = message;
    }

    public void incrementProcessed() {
        this.processedDates++;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public boolean isForce() {
        return force;
    }

    public BackfillJobStatus getStatus() {
        return status;
    }

    public int getTotalDates() {
        return totalDates;
    }

    public int getProcessedDates() {
        return processedDates;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getMessage() {
        return message;
    }
}
```

- [ ] **Step 4: Create the `BackfillJobDate` entity and its id class**

`src/main/java/com/marketpulse/refdata/entity/BackfillJobDateId.java`:

```java
package com.marketpulse.refdata.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite key of {@link BackfillJobDate}: one row per (job, date). */
public class BackfillJobDateId implements Serializable {

    private UUID jobId;
    private LocalDate tradeDate;

    public BackfillJobDateId() {
    }

    public BackfillJobDateId(UUID jobId, LocalDate tradeDate) {
        this.jobId = jobId;
        this.tradeDate = tradeDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BackfillJobDateId other)) {
            return false;
        }
        return Objects.equals(jobId, other.jobId) && Objects.equals(tradeDate, other.tradeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, tradeDate);
    }
}
```

`src/main/java/com/marketpulse/refdata/entity/BackfillJobDate.java`:

```java
package com.marketpulse.refdata.entity;

import com.marketpulse.refdata.model.BackfillDateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The outcome of one date within a backfill job. Holds no market data - only the audit trail. */
@Entity
@Table(name = "backfill_job_date")
@IdClass(BackfillJobDateId.class)
public class BackfillJobDate {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackfillDateStatus status;

    /** Row count written to equity_price for this date - a count, not the data itself. */
    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "message")
    private String message;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected BackfillJobDate() {
        // for JPA
    }

    public BackfillJobDate(UUID jobId, LocalDate tradeDate, BackfillDateStatus status, long rowCount, String message) {
        this.jobId = jobId;
        this.tradeDate = tradeDate;
        this.status = status;
        this.rowCount = rowCount;
        this.message = message;
        this.attemptedAt = Instant.now();
    }

    public UUID getJobId() {
        return jobId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BackfillDateStatus getStatus() {
        return status;
    }

    public long getRowCount() {
        return rowCount;
    }

    public String getMessage() {
        return message;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
```

- [ ] **Step 5: Create the two repositories and extend `EquityPriceRepository`**

`src/main/java/com/marketpulse/refdata/repository/BackfillJobRepository.java`:

```java
package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackfillJobRepository extends JpaRepository<BackfillJob, UUID> {

    /** The oldest job still PENDING or RUNNING, if any - used to reject a concurrent start. */
    Optional<BackfillJob> findFirstByStatusInOrderByCreatedAtAsc(Collection<BackfillJobStatus> statuses);

    /** All jobs in the given states - used at startup to reconcile jobs orphaned by a restart. */
    List<BackfillJob> findByStatusIn(Collection<BackfillJobStatus> statuses);

    List<BackfillJob> findTop50ByOrderByCreatedAtDesc();
}
```

`src/main/java/com/marketpulse/refdata/repository/BackfillJobDateRepository.java`:

```java
package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.entity.BackfillJobDateId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackfillJobDateRepository extends JpaRepository<BackfillJobDate, BackfillJobDateId> {

    List<BackfillJobDate> findByJobIdOrderByTradeDateAsc(UUID jobId);

    /**
     * Dates any prior job found NOT_FOUND - i.e. known non-trading days.
     * Spans all jobs regardless of the owning job's own status: a holiday discovered by a
     * job that later failed or was interrupted is still a holiday.
     */
    @Query("""
            SELECT DISTINCT d.tradeDate FROM BackfillJobDate d
            WHERE d.status = com.marketpulse.refdata.model.BackfillDateStatus.NOT_FOUND
              AND d.tradeDate BETWEEN :from AND :to
            """)
    List<LocalDate> findKnownNonTradingDates(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
```

Add to `EquityPriceRepository` (keeping its existing `findByTradeDateOrderBySymbolAsc`):

```java
    /** Distinct dates already loaded in the range - the primary skip set for a backfill. */
    @Query("SELECT DISTINCT p.tradeDate FROM EquityPrice p WHERE p.tradeDate BETWEEN :from AND :to")
    List<LocalDate> findDistinctTradeDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
```

with imports `org.springframework.data.jpa.repository.Query` and
`org.springframework.data.repository.query.Param`.

- [ ] **Step 6: Verify it compiles and the suite is still green**

Run: `mvn test`
Expected: PASS, 18 tests. (No new tests here — Task 7's integration test is what exercises
the migration against a real database. `mvn test` does not start a database, so nothing
here can be verified beyond compilation.)

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V2__create_backfill_job.sql src/main/java/com/marketpulse/refdata/model/BackfillJobStatus.java src/main/java/com/marketpulse/refdata/model/BackfillDateStatus.java src/main/java/com/marketpulse/refdata/entity/BackfillJob.java src/main/java/com/marketpulse/refdata/entity/BackfillJobDate.java src/main/java/com/marketpulse/refdata/entity/BackfillJobDateId.java src/main/java/com/marketpulse/refdata/repository/BackfillJobRepository.java src/main/java/com/marketpulse/refdata/repository/BackfillJobDateRepository.java src/main/java/com/marketpulse/refdata/repository/EquityPriceRepository.java
git commit -m "feat: add backfill_job schema, entities and repositories" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `BackfillService` — the synchronous walk

The core loop, kept synchronous and package-private (`executeJob`) so it is directly unit
testable without any async machinery. Task 5 wraps it.

**Files:**
- Create: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java`

**Interfaces:**
- Consumes: everything from Task 3, plus `NseProperties.getBackfill()` from Task 2 and
  `DownloadResult` (`status()`, `rowCount()`, `message()`).
- Produces, relied on by Tasks 5–7:
  - `BackfillService(BhavcopyService, EquityPriceRepository, BackfillJobRepository, BackfillJobDateRepository, NseProperties)`
  - `static List<LocalDate> weekdaysBetween(LocalDate from, LocalDate to)` (package-private)
  - `void executeJob(UUID jobId)` (package-private)
  - `BackfillJob createJob(LocalDate from, LocalDate to, boolean force)`
  - `Optional<BackfillJob> findActiveJob()`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java`:

```java
package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BackfillServiceTest {

    private BhavcopyService bhavcopyService;
    private EquityPriceRepository equityPriceRepository;
    private BackfillJobRepository jobRepository;
    private BackfillJobDateRepository jobDateRepository;
    private NseProperties properties;
    private BackfillService service;

    @BeforeEach
    void setUp() {
        bhavcopyService = mock(BhavcopyService.class);
        equityPriceRepository = mock(EquityPriceRepository.class);
        jobRepository = mock(BackfillJobRepository.class);
        jobDateRepository = mock(BackfillJobDateRepository.class);

        properties = new NseProperties();
        properties.getBackfill().setDelayMs(0L);
        properties.getBackfill().setMaxConsecutiveFailures(3);

        when(jobRepository.save(any(BackfillJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of());
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of());

        service = new BackfillService(
                bhavcopyService, equityPriceRepository, jobRepository, jobDateRepository, properties);
    }

    /** Registers a PENDING job with the mocked repository and returns it. */
    private BackfillJob givenJob(LocalDate from, LocalDate to, boolean force) {
        int totalDates = BackfillService.weekdaysBetween(from, to).size();
        BackfillJob job = new BackfillJob(from, to, force, totalDates);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    /** Every per-date row the service recorded, keyed by date. */
    private Map<LocalDate, BackfillJobDate> recordedDates() {
        ArgumentCaptor<BackfillJobDate> captor = ArgumentCaptor.forClass(BackfillJobDate.class);
        verify(jobDateRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream()
                .collect(Collectors.toMap(BackfillJobDate::getTradeDate, Function.identity()));
    }

    @Test
    void weekdaysBetweenExcludesWeekends() {
        // 2026-08-07 Fri, 08 Sat, 09 Sun, 10 Mon
        List<LocalDate> dates = BackfillService.weekdaysBetween(
                LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10));

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10));
    }

    @Test
    void weekdaysBetweenIsInclusiveOfBothEnds() {
        List<LocalDate> dates = BackfillService.weekdaysBetween(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10));

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 10));
    }

    @Test
    void skipsDatesAlreadyPresentInEquityPrice() {
        LocalDate loaded = LocalDate.of(2026, 8, 6);
        LocalDate missing = LocalDate.of(2026, 8, 7);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of(loaded));
        when(bhavcopyService.downloadBhavcopy(missing))
                .thenReturn(DownloadResult.success(missing, "equity_price", 2416));
        BackfillJob job = givenJob(loaded, missing, false);

        service.executeJob(job.getId());

        verify(bhavcopyService, never()).downloadBhavcopy(loaded);
        verify(bhavcopyService).downloadBhavcopy(missing);
        assertThat(recordedDates().get(loaded).getStatus()).isEqualTo(BackfillDateStatus.SKIPPED);
        assertThat(recordedDates().get(missing).getStatus()).isEqualTo(BackfillDateStatus.SUCCESS);
    }

    @Test
    void skipsDatesPreviouslyRecordedAsNotFound() {
        LocalDate holiday = LocalDate.of(2026, 8, 6);
        LocalDate tradingDay = LocalDate.of(2026, 8, 7);
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(holiday));
        when(bhavcopyService.downloadBhavcopy(tradingDay))
                .thenReturn(DownloadResult.success(tradingDay, "equity_price", 2416));
        BackfillJob job = givenJob(holiday, tradingDay, false);

        service.executeJob(job.getId());

        verify(bhavcopyService, never()).downloadBhavcopy(holiday);
        assertThat(recordedDates().get(holiday).getStatus()).isEqualTo(BackfillDateStatus.SKIPPED);
    }

    @Test
    void forceBypassesBothSkipSets() {
        LocalDate loaded = LocalDate.of(2026, 8, 6);
        LocalDate holiday = LocalDate.of(2026, 8, 7);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of(loaded));
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(holiday));
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenReturn(DownloadResult.success(loaded, "equity_price", 10));
        BackfillJob job = givenJob(loaded, holiday, true);

        service.executeJob(job.getId());

        verify(bhavcopyService).downloadBhavcopy(loaded);
        verify(bhavcopyService).downloadBhavcopy(holiday);
    }

    @Test
    void oneFailingDateDoesNotStopTheWalk() {
        LocalDate bad = LocalDate.of(2026, 8, 6);
        LocalDate good = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(bad)).thenReturn(DownloadResult.failure(bad, "boom"));
        when(bhavcopyService.downloadBhavcopy(good))
                .thenReturn(DownloadResult.success(good, "equity_price", 2416));
        BackfillJob job = givenJob(bad, good, false);

        service.executeJob(job.getId());

        verify(bhavcopyService).downloadBhavcopy(good);
        assertThat(recordedDates().get(bad).getStatus()).isEqualTo(BackfillDateStatus.FAILED);
        assertThat(recordedDates().get(bad).getMessage()).isEqualTo("boom");
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
    }

    @Test
    void abortsJobAfterMaxConsecutiveFailures() {
        // max-consecutive-failures is 3; Mon 3rd through Fri 7th is five weekdays.
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.failure(inv.getArgument(0), "blocked"));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.FAILED);
        assertThat(job.getMessage()).contains("3 consecutive");
        verify(bhavcopyService, org.mockito.Mockito.times(3)).downloadBhavcopy(any());
        verify(bhavcopyService, never()).downloadBhavcopy(LocalDate.of(2026, 8, 6));
    }

    @Test
    void successResetsTheConsecutiveFailureCounter() {
        LocalDate d1 = LocalDate.of(2026, 8, 3);
        LocalDate d2 = LocalDate.of(2026, 8, 4);
        LocalDate d3 = LocalDate.of(2026, 8, 5);
        LocalDate d4 = LocalDate.of(2026, 8, 6);
        LocalDate d5 = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(d1)).thenReturn(DownloadResult.failure(d1, "x"));
        when(bhavcopyService.downloadBhavcopy(d2)).thenReturn(DownloadResult.failure(d2, "x"));
        when(bhavcopyService.downloadBhavcopy(d3)).thenReturn(DownloadResult.success(d3, "equity_price", 5));
        when(bhavcopyService.downloadBhavcopy(d4)).thenReturn(DownloadResult.failure(d4, "x"));
        when(bhavcopyService.downloadBhavcopy(d5)).thenReturn(DownloadResult.failure(d5, "x"));
        BackfillJob job = givenJob(d1, d5, false);

        service.executeJob(job.getId());

        // 2 failures, reset, then 2 more - never 3 in a row, so the job runs to completion.
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        verify(bhavcopyService).downloadBhavcopy(d5);
    }

    @Test
    void notFoundDoesNotAdvanceTheCircuitBreaker() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.notFound(inv.getArgument(0)));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        verify(bhavcopyService, org.mockito.Mockito.times(5)).downloadBhavcopy(any());
        assertThat(recordedDates().get(to).getStatus()).isEqualTo(BackfillDateStatus.NOT_FOUND);
    }

    @Test
    void marksJobRunningThenCompletedAndCountsEveryDate() {
        LocalDate from = LocalDate.of(2026, 8, 6);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.success(inv.getArgument(0), "equity_price", 100));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getProcessedDates()).isEqualTo(2);
    }

    @Test
    void createJobCountsWeekdaysAndStartsPending() {
        BackfillJob job = service.createJob(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10), false);

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.PENDING);
        assertThat(job.getTotalDates()).isEqualTo(2);
        verify(jobRepository).save(eq(job));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=BackfillServiceTest`
Expected: FAIL — compilation error, `BackfillService` does not exist.

- [ ] **Step 3: Implement `BackfillService`**

Create `src/main/java/com/marketpulse/refdata/service/BackfillService.java`:

```java
package com.marketpulse.refdata.service;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks a date range and pulls each missing trading day's Bhavcopy.
 *
 * <p>Deliberately a separate bean from {@link BhavcopyService}: calling
 * {@code downloadBhavcopy} across a bean boundary means Spring's {@code @Transactional}
 * proxy actually applies, so every date commits in its own transaction and a failure late
 * in a multi-year run cannot roll back the days already loaded.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private final BhavcopyService bhavcopyService;
    private final EquityPriceRepository equityPriceRepository;
    private final BackfillJobRepository jobRepository;
    private final BackfillJobDateRepository jobDateRepository;
    private final NseProperties properties;

    public BackfillService(
            BhavcopyService bhavcopyService,
            EquityPriceRepository equityPriceRepository,
            BackfillJobRepository jobRepository,
            BackfillJobDateRepository jobDateRepository,
            NseProperties properties) {
        this.bhavcopyService = bhavcopyService;
        this.equityPriceRepository = equityPriceRepository;
        this.jobRepository = jobRepository;
        this.jobDateRepository = jobDateRepository;
        this.properties = properties;
    }

    /** Every weekday in [from, to] inclusive. Weekends are never fetched - NSE publishes no file. */
    static List<LocalDate> weekdaysBetween(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
        }
        return dates;
    }

    /** Persists a new PENDING job. Callers validate the range before calling this. */
    @Transactional
    public BackfillJob createJob(LocalDate from, LocalDate to, boolean force) {
        BackfillJob job = new BackfillJob(from, to, force, weekdaysBetween(from, to).size());
        jobRepository.save(job);
        log.info("Created backfill job {} for {}..{} ({} weekdays, force={})",
                job.getId(), from, to, job.getTotalDates(), force);
        return job;
    }

    /** The oldest job still PENDING or RUNNING, if any. */
    public Optional<BackfillJob> findActiveJob() {
        return jobRepository.findFirstByStatusInOrderByCreatedAtAsc(
                List.of(BackfillJobStatus.PENDING, BackfillJobStatus.RUNNING));
    }

    /**
     * Runs the whole range synchronously. Package-private so it can be unit tested without
     * any async machinery; {@code runJob} is the public async entry point.
     */
    void executeJob(UUID jobId) {
        BackfillJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Backfill job {} vanished before it could run", jobId);
            return;
        }

        job.markRunning();
        jobRepository.save(job);
        log.info("Backfill job {} started: {}..{}", jobId, job.getFromDate(), job.getToDate());

        Set<LocalDate> skip = skipSet(job);
        int maxConsecutiveFailures = properties.getBackfill().getMaxConsecutiveFailures();
        int consecutiveFailures = 0;

        for (LocalDate date : weekdaysBetween(job.getFromDate(), job.getToDate())) {
            if (skip.contains(date)) {
                record(job, date, BackfillDateStatus.SKIPPED, 0, "already loaded or known non-trading day");
                continue;
            }

            DownloadResult result = bhavcopyService.downloadBhavcopy(date);
            switch (result.status()) {
                case SUCCESS -> {
                    record(job, date, BackfillDateStatus.SUCCESS, result.rowCount(), null);
                    consecutiveFailures = 0;
                }
                case NOT_FOUND -> record(job, date, BackfillDateStatus.NOT_FOUND, 0, result.message());
                case FAILURE -> {
                    record(job, date, BackfillDateStatus.FAILED, 0, result.message());
                    consecutiveFailures++;
                }
            }

            if (consecutiveFailures >= maxConsecutiveFailures) {
                String message = "Aborted after " + consecutiveFailures
                        + " consecutive failures, last at " + date + ": " + result.message();
                job.markFailed(message);
                jobRepository.save(job);
                log.error("Backfill job {} {}", jobId, message);
                return;
            }

            sleepBetweenDates();
        }

        job.markCompleted();
        jobRepository.save(job);
        log.info("Backfill job {} completed: {}/{} dates processed",
                jobId, job.getProcessedDates(), job.getTotalDates());
    }

    /** Dates we must not fetch: already loaded, or already known to be non-trading days. */
    private Set<LocalDate> skipSet(BackfillJob job) {
        if (job.isForce()) {
            return Set.of();
        }
        Set<LocalDate> skip = new HashSet<>(
                equityPriceRepository.findDistinctTradeDatesBetween(job.getFromDate(), job.getToDate()));
        skip.addAll(jobDateRepository.findKnownNonTradingDates(job.getFromDate(), job.getToDate()));
        return skip;
    }

    private void record(BackfillJob job, LocalDate date, BackfillDateStatus status, long rowCount, String message) {
        jobDateRepository.save(new BackfillJobDate(job.getId(), date, status, rowCount, message));
        job.incrementProcessed();
        jobRepository.save(job);
    }

    private void sleepBetweenDates() {
        long delayMs = properties.getBackfill().getDelayMs();
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=BackfillServiceTest`
Expected: PASS, 11 tests.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS, 29 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marketpulse/refdata/service/BackfillService.java src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java
git commit -m "feat: add BackfillService date-range walk with skip sets and circuit breaker" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Async execution and startup reconciliation

**Files:**
- Create: `src/main/java/com/marketpulse/refdata/config/AsyncConfig.java`
- Create: `src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java`
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillStartupReconcilerTest.java`
- Modify: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`

**Interfaces:**
- Consumes: `BackfillService.executeJob(UUID)` and `BackfillJobRepository.findByStatusIn(...)` from Tasks 3–4.
- Produces: `BackfillService.runJob(UUID)` — the `@Async` entry point Task 6's controller calls.

- [ ] **Step 1: Add the executor**

Create `src/main/java/com/marketpulse/refdata/config/AsyncConfig.java`:

```java
package com.marketpulse.refdata.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Async plumbing for long-running backfill jobs. */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Single-threaded on purpose: two backfills must never hit NSE concurrently, which
     * would double our request rate and risk being blocked mid-run.
     */
    @Bean("backfillExecutor")
    public Executor backfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("backfill-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: Add the async entry point to `BackfillService`**

Append this method to `BackfillService`, directly above `executeJob`:

```java
    /**
     * Async entry point. Returns immediately; the walk runs on the single backfill thread.
     * Kept as a thin wrapper so {@code executeJob} stays synchronously testable.
     */
    @Async("backfillExecutor")
    public void runJob(UUID jobId) {
        try {
            executeJob(jobId);
        } catch (RuntimeException e) {
            log.error("Backfill job {} threw unexpectedly: {}", jobId, e.getMessage(), e);
            jobRepository.findById(jobId).ifPresent(job -> {
                job.markFailed("Unexpected error: " + e.getMessage());
                jobRepository.save(job);
            });
        }
    }
```

Add the import `org.springframework.scheduling.annotation.Async`.

- [ ] **Step 3: Write the failing reconciler test**

Create `src/test/java/com/marketpulse/refdata/service/BackfillStartupReconcilerTest.java`:

```java
package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackfillStartupReconcilerTest {

    @Test
    void marksJobsOrphanedByARestartAsInterrupted() {
        BackfillJobRepository jobRepository = mock(BackfillJobRepository.class);
        BackfillJob orphan = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        orphan.markRunning();
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of(orphan));

        new BackfillStartupReconciler(jobRepository).run(null);

        assertThat(orphan.getStatus()).isEqualTo(BackfillJobStatus.INTERRUPTED);
        assertThat(orphan.getMessage()).contains("restart");
        verify(jobRepository).save(orphan);
    }

    @Test
    void doesNothingWhenNoJobsWereLeftRunning() {
        BackfillJobRepository jobRepository = mock(BackfillJobRepository.class);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

        new BackfillStartupReconciler(jobRepository).run(null);

        verify(jobRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn test -Dtest=BackfillStartupReconcilerTest`
Expected: FAIL — compilation error, `BackfillStartupReconciler` does not exist.

- [ ] **Step 5: Implement the reconciler**

Create `src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java`:

```java
package com.marketpulse.refdata.service;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Reconciles jobs orphaned by a restart. A PENDING or RUNNING job cannot still be live -
 * the JVM that owned it is gone - so leaving that status in place would report a lie.
 *
 * <p>Deliberately does NOT auto-resume: a restart loop must never silently generate an
 * hour and a half of NSE traffic nobody asked for. Recovery is re-POSTing the same range,
 * which is cheap because the skip sets drop everything already loaded.
 */
@Component
public class BackfillStartupReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillStartupReconciler.class);

    private final BackfillJobRepository jobRepository;

    public BackfillStartupReconciler(BackfillJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BackfillJob> orphaned = jobRepository.findByStatusIn(
                List.of(BackfillJobStatus.PENDING, BackfillJobStatus.RUNNING));

        for (BackfillJob job : orphaned) {
            job.markInterrupted("Interrupted by an application restart after "
                    + job.getProcessedDates() + "/" + job.getTotalDates()
                    + " dates. Re-POST the same range to resume; loaded dates are skipped.");
            jobRepository.save(job);
            log.warn("Backfill job {} was interrupted by a restart at {}/{} dates - "
                            + "re-POST {}..{} to resume",
                    job.getId(), job.getProcessedDates(), job.getTotalDates(),
                    job.getFromDate(), job.getToDate());
        }
    }
}
```

- [ ] **Step 6: Run the whole suite**

Run: `mvn test`
Expected: PASS, 31 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/marketpulse/refdata/config/AsyncConfig.java src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java src/test/java/com/marketpulse/refdata/service/BackfillStartupReconcilerTest.java src/main/java/com/marketpulse/refdata/service/BackfillService.java
git commit -m "feat: run backfill jobs async and reconcile orphaned jobs at startup" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: REST API

**Files:**
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillDateResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillJobResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/model/BackfillErrorResponse.java`
- Create: `src/main/java/com/marketpulse/refdata/controller/BackfillController.java`
- Create: `src/test/java/com/marketpulse/refdata/controller/BackfillControllerTest.java`
- Modify: `src/main/java/com/marketpulse/refdata/service/BackfillService.java`

**Interfaces:**
- Consumes: `BackfillService.createJob`, `runJob`, `findActiveJob` from Tasks 4–5.
- Produces: `BackfillService.getJobDetail(UUID)` → `Optional<BackfillJobResponse>` and
  `BackfillService.listRecentJobs()` → `List<BackfillJobResponse>`.

**Note on the conflict check:** `findActiveJob()` then `createJob()` is not atomic. Two
simultaneous POSTs could both pass. This is an operator-driven utility endpoint used a
handful of times, and the single-thread executor still serialises the actual work, so the
race is accepted rather than locked against.

- [ ] **Step 1: Create the response records**

`src/main/java/com/marketpulse/refdata/model/BackfillDateResponse.java`:

```java
package com.marketpulse.refdata.model;

import java.time.LocalDate;

/** One date's outcome within a backfill job. {@code rowCount} is a count, not the data. */
public record BackfillDateResponse(LocalDate tradeDate, BackfillDateStatus status, long rowCount, String message) {
}
```

`src/main/java/com/marketpulse/refdata/model/BackfillErrorResponse.java`:

```java
package com.marketpulse.refdata.model;

/** Error body for a rejected backfill request. */
public record BackfillErrorResponse(String message) {
}
```

`src/main/java/com/marketpulse/refdata/model/BackfillJobResponse.java`:

```java
package com.marketpulse.refdata.model;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API view of a backfill job. {@code results} is null on the list endpoint. */
public record BackfillJobResponse(
        UUID jobId,
        LocalDate fromDate,
        LocalDate toDate,
        BackfillJobStatus status,
        boolean force,
        int totalDates,
        int processedDates,
        long succeeded,
        long skipped,
        long notFound,
        long failed,
        long totalRows,
        Instant startedAt,
        Instant finishedAt,
        String message,
        List<BackfillDateResponse> results) {

    /** Summary without the per-date breakdown. */
    public static BackfillJobResponse summary(BackfillJob job, List<BackfillJobDate> dates) {
        return build(job, dates, null);
    }

    /** Full detail including every date attempted. */
    public static BackfillJobResponse detail(BackfillJob job, List<BackfillJobDate> dates) {
        List<BackfillDateResponse> results = dates.stream()
                .map(d -> new BackfillDateResponse(d.getTradeDate(), d.getStatus(), d.getRowCount(), d.getMessage()))
                .toList();
        return build(job, dates, results);
    }

    private static BackfillJobResponse build(
            BackfillJob job, List<BackfillJobDate> dates, List<BackfillDateResponse> results) {
        return new BackfillJobResponse(
                job.getId(),
                job.getFromDate(),
                job.getToDate(),
                job.getStatus(),
                job.isForce(),
                job.getTotalDates(),
                job.getProcessedDates(),
                count(dates, BackfillDateStatus.SUCCESS),
                count(dates, BackfillDateStatus.SKIPPED),
                count(dates, BackfillDateStatus.NOT_FOUND),
                count(dates, BackfillDateStatus.FAILED),
                dates.stream().mapToLong(BackfillJobDate::getRowCount).sum(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getMessage(),
                results);
    }

    private static long count(List<BackfillJobDate> dates, BackfillDateStatus status) {
        return dates.stream().filter(d -> d.getStatus() == status).count();
    }
}
```

- [ ] **Step 2: Add the two read methods to `BackfillService`**

Append to `BackfillService`:

```java
    /** Full job detail including every date attempted. */
    public Optional<BackfillJobResponse> getJobDetail(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(job -> BackfillJobResponse.detail(job, jobDateRepository.findByJobIdOrderByTradeDateAsc(jobId)));
    }

    /** The 50 most recent jobs, newest first, without per-date breakdowns. */
    public List<BackfillJobResponse> listRecentJobs() {
        return jobRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(job -> BackfillJobResponse.summary(
                        job, jobDateRepository.findByJobIdOrderByTradeDateAsc(job.getId())))
                .toList();
    }
```

Add the import `com.marketpulse.refdata.model.BackfillJobResponse`.

- [ ] **Step 3: Write the failing controller tests**

Create `src/test/java/com/marketpulse/refdata/controller/BackfillControllerTest.java`:

```java
package com.marketpulse.refdata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.service.BackfillService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BackfillController.class)
class BackfillControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        NseProperties nseProperties() {
            return new NseProperties();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackfillService backfillService;

    @BeforeEach
    void setUp() {
        when(backfillService.findActiveJob()).thenReturn(Optional.empty());
    }

    @Test
    void startReturnsAcceptedWithAJobId() throws Exception {
        BackfillJob job = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        when(backfillService.createJob(any(), any(), anyBoolean())).thenReturn(job);

        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-07"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalDates").value(5));

        verify(backfillService).runJob(job.getId());
    }

    @Test
    void rejectsRangeWhereFromIsAfterTo() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-07")
                        .param("to", "2026-08-03"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsRangeEndingInTheFuture() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2099-01-01"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsRangeStartingBeforeTheArchiveExists() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2015-01-01")
                        .param("to", "2026-08-07"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsASecondJobWhileOneIsActive() throws Exception {
        BackfillJob running = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        when(backfillService.findActiveJob()).thenReturn(Optional.of(running));

        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-07"))
                .andExpect(status().isConflict());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void getJobReturnsNotFoundForAnUnknownId() throws Exception {
        when(backfillService.getJobDetail(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bhavcopy/backfill/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJobsReturnsOk() throws Exception {
        when(backfillService.listRecentJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bhavcopy/backfill"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `mvn test -Dtest=BackfillControllerTest`
Expected: FAIL — compilation error, `BackfillController` does not exist.

- [ ] **Step 5: Implement the controller**

Create `src/main/java/com/marketpulse/refdata/controller/BackfillController.java`:

```java
package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillErrorResponse;
import com.marketpulse.refdata.model.BackfillJobResponse;
import com.marketpulse.refdata.service.BackfillService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bulk historical backfill over a date range. Utility endpoints - not part of the daily flow. */
@RestController
@RequestMapping("/api/v1/bhavcopy/backfill")
public class BackfillController {

    private final BackfillService backfillService;
    private final NseProperties properties;

    public BackfillController(BackfillService backfillService, NseProperties properties) {
        this.backfillService = backfillService;
        this.properties = properties;
    }

    /** Queues a backfill over [from, to]. Returns 202 immediately; the walk runs in the background. */
    @PostMapping
    public ResponseEntity<Object> start(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean force) {

        LocalDate earliest = properties.getBackfill().getEarliestDate();
        if (from.isAfter(to)) {
            return badRequest("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
        if (to.isAfter(LocalDate.now())) {
            return badRequest("'to' (" + to + ") must not be in the future");
        }
        if (from.isBefore(earliest)) {
            return badRequest("'from' (" + from + ") is before " + earliest
                    + ", the earliest date NSE serves a usable Bhavcopy archive file");
        }
        if (backfillService.findActiveJob().isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BackfillErrorResponse("A backfill job is already pending or running"));
        }

        BackfillJob job = backfillService.createJob(from, to, force);
        backfillService.runJob(job.getId());
        return ResponseEntity.accepted().body(BackfillJobResponse.summary(job, List.of()));
    }

    /** Progress and per-date breakdown for one job. */
    @GetMapping("/{jobId}")
    public ResponseEntity<BackfillJobResponse> getJob(@PathVariable UUID jobId) {
        return backfillService.getJobDetail(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The 50 most recent jobs, newest first. */
    @GetMapping
    public List<BackfillJobResponse> listJobs() {
        return backfillService.listRecentJobs();
    }

    private static ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(new BackfillErrorResponse(message));
    }
}
```

- [ ] **Step 6: Run the whole suite**

Run: `mvn test`
Expected: PASS, 38 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/marketpulse/refdata/model/BackfillDateResponse.java src/main/java/com/marketpulse/refdata/model/BackfillJobResponse.java src/main/java/com/marketpulse/refdata/model/BackfillErrorResponse.java src/main/java/com/marketpulse/refdata/controller/BackfillController.java src/test/java/com/marketpulse/refdata/controller/BackfillControllerTest.java src/main/java/com/marketpulse/refdata/service/BackfillService.java
git commit -m "feat: add backfill REST endpoints with range validation" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Integration test

The only test that touches a real database: proves `V2` applies on top of `V1`, that job
rows persist, and that a second run over the same range skips everything already loaded.

**Files:**
- Create: `src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java`

**Interfaces:**
- Consumes: everything from Tasks 2–6.

- [ ] **Step 1: Write the integration test**

Create `src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java`. Mirror the
container setup already used by `BhavcopyServiceIT` (`@ServiceConnection`, so no manual
datasource properties), with `NseHttpClient` mocked so no NSE traffic occurs.

```java
package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "nse.scheduler.enabled=false",
        "nse.backfill.delay-ms=0"
})
class BackfillServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> timescaledb = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    /** Two EQ rows dated 2026-08-06 - DATE1 must match the requested date or the guard rejects it. */
    private static byte[] csvFor(LocalDate date) {
        String date1 = String.format("%02d-%s-%d",
                date.getDayOfMonth(),
                date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                date.getYear());
        return ("""
                SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
                20MICRONS, EQ, %s, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
                AARTIIND, EQ, %s, 1785.50, 1788.00, 1801.55, 1780.70, 1783.00, 1782.30, 1784.81, 41969, 749.07, 2541, 32915, 78.43
                """).formatted(date1, date1).getBytes(StandardCharsets.UTF_8);
    }

    @MockBean
    private NseHttpClient nseHttpClient;

    @Autowired
    private BackfillService backfillService;

    @Autowired
    private BackfillJobRepository jobRepository;

    @Autowired
    private BackfillJobDateRepository jobDateRepository;

    @Autowired
    private EquityPriceRepository equityPriceRepository;

    @Test
    void runsARangeThenSkipsEverythingOnASecondRun() throws Exception {
        LocalDate thursday = LocalDate.of(2026, 8, 6);
        LocalDate friday = LocalDate.of(2026, 8, 7);
        when(nseHttpClient.downloadFile(anyString()))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    return url.contains("06082026") ? csvFor(thursday) : csvFor(friday);
                });

        BackfillJob first = backfillService.createJob(thursday, friday, false);
        backfillService.executeJob(first.getId());

        BackfillJob reloadedFirst = jobRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        assertThat(reloadedFirst.getProcessedDates()).isEqualTo(2);
        assertThat(equityPriceRepository.findDistinctTradeDatesBetween(thursday, friday))
                .containsExactlyInAnyOrder(thursday, friday);

        List<BackfillJobDate> firstDates = jobDateRepository.findByJobIdOrderByTradeDateAsc(first.getId());
        assertThat(firstDates).hasSize(2);
        assertThat(firstDates).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(BackfillDateStatus.SUCCESS));
        assertThat(firstDates.get(0).getRowCount()).isEqualTo(2);

        // Second run over the same range: everything is already loaded, so nothing is fetched.
        BackfillJob second = backfillService.createJob(thursday, friday, false);
        backfillService.executeJob(second.getId());

        List<BackfillJobDate> secondDates = jobDateRepository.findByJobIdOrderByTradeDateAsc(second.getId());
        assertThat(secondDates).hasSize(2);
        assertThat(secondDates).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(BackfillDateStatus.SKIPPED));
    }

    @Test
    void recordsHolidaysAsNotFoundAndSkipsThemNextTime() throws Exception {
        LocalDate holiday = LocalDate.of(2026, 8, 10);
        when(nseHttpClient.downloadFile(anyString())).thenThrow(new NseHttpException(404, "Not Found"));

        BackfillJob first = backfillService.createJob(holiday, holiday, false);
        backfillService.executeJob(first.getId());

        assertThat(jobDateRepository.findByJobIdOrderByTradeDateAsc(first.getId()).get(0).getStatus())
                .isEqualTo(BackfillDateStatus.NOT_FOUND);
        assertThat(jobDateRepository.findKnownNonTradingDates(holiday, holiday)).containsExactly(holiday);

        BackfillJob second = backfillService.createJob(holiday, holiday, false);
        backfillService.executeJob(second.getId());

        assertThat(jobDateRepository.findByJobIdOrderByTradeDateAsc(second.getId()).get(0).getStatus())
                .isEqualTo(BackfillDateStatus.SKIPPED);
    }
}
```

- [ ] **Step 2: Run the integration test**

Needs a running Docker daemon. It will not run under plain `mvn test`.

Run: `mvn test -Dtest=BackfillServiceIT`
Expected: PASS, 2 tests. If it fails on the Flyway step, the `V2` migration and the JPA
entity mappings disagree — `ddl-auto: validate` is what surfaces that.

- [ ] **Step 3: Confirm the unit suite is unaffected**

Run: `mvn test`
Expected: PASS, 38 tests (the IT is still excluded).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java
git commit -m "test: add Testcontainers IT for backfill migration and skip-on-rerun" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation

**Files:**
- Modify: `CLAUDE.md` (repo root)

- [ ] **Step 1: Update the package layout section**

In `CLAUDE.md`, add these entries to the numbered "Package layout" list, after the existing
entry 9 (`scheduler/BhavcopyScheduler.java`), renumbering nothing else:

```markdown
10. **[service/BackfillService.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillService.java)** — bulk historical loader. Walks the weekdays in a date range and calls `BhavcopyService.downloadBhavcopy` once per date. **Deliberately a separate bean**: calling across a bean boundary means the `@Transactional` proxy on `downloadBhavcopy` actually applies, so each date commits independently and a failure late in a multi-year run can't roll back the days already loaded. Runs `@Async` on a single-thread `backfillExecutor` so two backfills never hit NSE at once. Skips dates already in `equity_price` and dates any prior job recorded `NOT_FOUND` (holidays), which is what makes a re-POST of the same range resume cheaply; `force=true` bypasses both.
11. **[controller/BackfillController.java](markets-ref-data/src/main/java/com/marketpulse/refdata/controller/BackfillController.java)** — `POST /api/v1/bhavcopy/backfill?from=&to=[&force=]` (202 + jobId, 409 if one is already active), `GET /api/v1/bhavcopy/backfill/{jobId}`, `GET /api/v1/bhavcopy/backfill`. Rejects `from > to`, a future `to`, and any `from` before `nse.backfill.earliest-date`.
12. **[service/BackfillStartupReconciler.java](markets-ref-data/src/main/java/com/marketpulse/refdata/service/BackfillStartupReconciler.java)** — on boot, marks PENDING/RUNNING jobs `INTERRUPTED`; the JVM that owned them is gone. Does **not** auto-resume, so a restart loop can't silently generate ~90 minutes of NSE traffic. Recovery is re-POSTing the same range.
```

- [ ] **Step 2: Document the archive limits**

Add to the "Known operational constraints" section in `CLAUDE.md`:

```markdown
- The `sec_bhavdata_full_DDMMYYYY.csv` archive pattern only goes back to **2019-10-01** (~6.9 years). Earlier dates 404. Older history exists under NSE's legacy `cm<DD><MON><YYYY>bhav.csv.zip` path but lacks the delivery columns this schema requires.
- **`sec_bhavdata_full_30092019.csv` returns HTTP 200 with rows dated `27-Jun-2019`.** `BhavcopyService` therefore validates the CSV's `DATE1` column against the requested date and refuses to persist a mismatch — without that guard, `trade_date` is stamped from the requested date and bad data lands silently. This is why `nse.backfill.earliest-date` is 2019-10-01, not 2019-09-30.
```

- [ ] **Step 3: Update the testing conventions section**

Add to the "Testing conventions" list in `CLAUDE.md`:

```markdown
- **[service/BackfillServiceTest.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceTest.java)** mocks `BhavcopyService` and all three repositories, and sets `delay-ms` to 0 so the throttle doesn't slow the suite. Covers weekend exclusion, both skip sets, `force`, per-date failure isolation, and the consecutive-failure circuit breaker.
- **[service/BackfillServiceIT.java](markets-ref-data/src/test/java/com/marketpulse/refdata/service/BackfillServiceIT.java)** is the second Testcontainers test — same opt-in rules as `BhavcopyServiceIT`. Covers the `V2` migration and skip-on-re-run.
```

- [ ] **Step 4: Update the commands section**

In the `## Commands` code block in `CLAUDE.md`, add:

```powershell
# Run the backfill integration test (opt-in, needs a running Docker daemon)
mvn test -Dtest=BackfillServiceIT
```

And update the `mvn test` note: the suite is now **38 tests**, still needing neither Docker
nor network.

- [ ] **Step 5: Commit**

```bash
git add ../CLAUDE.md
git commit -m "docs: document the backfill API and NSE archive limits" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Manual verification (after Task 8)

Not a substitute for the tests — a smoke check that the wiring holds end to end.

```powershell
docker compose up -d --build

# a short, already-loaded range: every date should come back SKIPPED
curl -X POST "http://localhost:8081/api/v1/bhavcopy/backfill?from=2026-08-03&to=2026-08-07"
# -> 202 with a jobId

curl "http://localhost:8081/api/v1/bhavcopy/backfill/<jobId>"
# -> skipped: 5, succeeded: 0  (Task 1's data is already in equity_price)

# validation
curl -X POST "http://localhost:8081/api/v1/bhavcopy/backfill?from=2015-01-01&to=2026-08-07"
# -> 400, "is before 2019-10-01"
```

The real 5-year load is then:

```powershell
curl -X POST "http://localhost:8081/api/v1/bhavcopy/backfill?from=2021-08-09&to=2026-08-09"
# 1,305 weekday candidates, ~87 min
```
