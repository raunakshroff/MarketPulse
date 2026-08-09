# Bhavcopy Date-Range Backfill API — Design

**Date:** 2026-08-09
**Module:** `markets-ref-data`
**Status:** Approved for planning

## Problem

`POST /api/v1/bhavcopy/download?date=...` pulls one trading day. Loading history means
driving it in an external loop — which is how the 2026-07-09 → 2026-08-09 backfill was
run. That works for a month but not for years: there is no record of what was attempted,
no way to resume after a restart, and no protection against NSE serving a bad file.

This adds a first-class backfill API for bulk historical loads. It is a utility endpoint,
expected to be used rarely — primarily for an initial ~5-year load.

## Research findings (empirical, 2026-08-09)

Probed `https://nsearchives.nseindia.com/products/content/sec_bhavdata_full_DDMMYYYY.csv`
directly.

| Question | Finding |
|---|---|
| Earliest date returning HTTP 200 | 2019-09-30 |
| Earliest **trustworthy** date | **2019-10-01** |
| 2019-09-27 and earlier (probed back to 2011-01-03) | 404 — pattern does not exist |
| History available | ~6.9 years |
| CSV schema across the range | Identical 15 columns; parser-compatible throughout |

Two consequences:

1. **A 5-year load is unconstrained.** Five years back from 2026-08-09 is 2021-08-09,
   comfortably inside the window.
2. **`sec_bhavdata_full_30092019.csv` is corrupt.** It returns HTTP 200 but its rows carry
   `DATE1 = 27-Jun-2019`. Every other date probed matches its filename.
   `BhavcopyService.parseToEntities` stamps `trade_date` from the *requested* date and
   never reads `DATE1`, so this file would silently persist June prices under a September
   trade date. It is a 200, not an error, so nothing currently flags it.

Pre-October-2019 data exists under NSE's legacy `cm<DD><MON><YYYY>bhav.csv.zip` path, but
that format lacks the delivery columns (`DELIV_QTY`, `DELIV_PER`) this schema requires.
Out of scope.

## Design

### Component boundaries

A new `BackfillService` orchestrates the date walk. It calls the existing
`BhavcopyService.downloadBhavcopy(date)` unchanged, once per date.

`BackfillService` is a separate bean, so Spring's `@Transactional` proxy on
`downloadBhavcopy` genuinely applies: **each date commits in its own transaction**. A
failure on date 900 cannot roll back the previous 899. Looping *inside* `BhavcopyService`
would self-invoke past the proxy and wrap a 5-year load in a single transaction — this
boundary is chosen deliberately to avoid that.

Execution is `@Async` on a dedicated **single-thread** executor, so two backfill jobs can
never run against NSE concurrently.

```
BhavcopyController ──POST /backfill──> BackfillService (@Async, 1 thread)
                                            │
                                            ├─ per date ─> BhavcopyService.downloadBhavcopy (@Transactional)
                                            │                   └─> NseHttpClient → EquityCsvFilter → upsertAll
                                            └─ per date ─> BackfillJobRepository (progress + audit)
```

### Schema — `V2__create_backfill_job.sql`

A new migration. `V1` is never edited.

```sql
CREATE TABLE backfill_job (
    id               UUID         NOT NULL PRIMARY KEY,
    from_date        DATE         NOT NULL,
    to_date          DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    total_dates      INTEGER      NOT NULL,
    processed_dates  INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    message          TEXT
);

CREATE TABLE backfill_job_date (
    job_id       UUID        NOT NULL REFERENCES backfill_job (id),
    trade_date   DATE        NOT NULL,
    status       VARCHAR(20) NOT NULL,
    row_count    BIGINT      NOT NULL DEFAULT 0,
    message      TEXT,
    attempted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_backfill_job_date PRIMARY KEY (job_id, trade_date)
);

CREATE INDEX idx_backfill_job_date_status ON backfill_job_date (status, trade_date);
```

Plain tables — **not** hypertables. These are low-volume operational records, not time
series. Entities must match exactly (`ddl-auto: validate`).

- `backfill_job.status` ∈ `PENDING | RUNNING | COMPLETED | FAILED | INTERRUPTED`
- `backfill_job_date.status` ∈ `SUCCESS | NOT_FOUND | FAILED | SKIPPED`

### Date selection and skip logic

For a requested range, candidate dates are the **weekdays** in `[from, to]`. Saturdays and
Sundays are excluded outright — NSE publishes no file, so fetching them is a guaranteed
404.

Before the walk, two queries build the skip set:

```sql
-- already loaded
SELECT DISTINCT trade_date FROM equity_price WHERE trade_date BETWEEN ? AND ?;

-- known non-trading days (holidays) from prior jobs
SELECT DISTINCT trade_date FROM backfill_job_date
 WHERE status = 'NOT_FOUND' AND trade_date BETWEEN ? AND ?;
```

The `NOT_FOUND` lookup spans **all** prior jobs regardless of the owning job's own status —
a holiday discovered by a job that later failed or was interrupted is still a holiday.

Dates in either set are recorded `SKIPPED` and never fetched. This delivers the required
behavior — *only fetch dates whose data is not already persisted* — and makes resumption
automatic: re-POST the same range and only the gaps are pulled. Re-running an
already-complete range makes ~0 network calls.

`force=true` bypasses both skip sets and re-fetches every candidate date. Upserts make
this safe.

### `DATE1` validation

New guard in `BhavcopyService.parseToEntities`: parse the CSV's `DATE1` column and compare
it to the requested date. On mismatch, abort that date with
`DownloadResult.failure(date, ...)` and a message naming both dates; persist nothing.

This is what stops the `2019-09-30` file from landing June prices under a September trade
date, and guards against any future recurrence. It applies to the single-date endpoint too,
not just backfill.

### Pacing and failure handling

Configuration under `nse.backfill`:

```yaml
nse:
  backfill:
    delay-ms: 2000                # pause between dates
    max-consecutive-failures: 10  # circuit breaker
    earliest-date: 2019-10-01     # from the research above
```

- **Throttle:** `delay-ms` between dates, default 2000 — the pace already validated across
  22 consecutive dates with zero rate-limiting. A 5-year load is 1,305 weekday candidates
  at ~4s each (2s throttle + ~2s fetch) ≈ **87 min**.
- **Per-date failures are isolated:** recorded as `FAILED` with the message; the walk
  continues. The job still ends `COMPLETED`, with counts telling the story.
- **Circuit breaker:** `max-consecutive-failures` consecutive failures aborts the job as
  `FAILED`. If NSE starts blocking mid-run, this stops the job rather than burning 70 more
  minutes on 403s. Any success resets the counter.
- `NOT_FOUND` is an expected outcome (holiday / not yet published), **not** a failure, and
  does not advance the circuit breaker.

### API

| Endpoint | Behavior |
|---|---|
| `POST /api/v1/bhavcopy/backfill?from=&to=[&force=]` | `202` + job summary; starts async |
| `GET /api/v1/bhavcopy/backfill/{jobId}` | Job progress, counts, per-date results |
| `GET /api/v1/bhavcopy/backfill` | 50 most recent jobs, newest first |

`POST` creates the job as `PENDING` and returns that immediately; the worker thread flips
it to `RUNNING` when it picks the job up.

Request validation, all rejected `400` before any job is created:

- `from` and `to` both required and parseable
- `from <= to`
- `to` not in the future
- `from >= nse.backfill.earliest-date`

A typo'd year therefore fails immediately instead of grinding through 1,500 certain 404s.

`409 Conflict` if a job is already `RUNNING` — one backfill at a time.
`404` for an unknown `jobId`.

Response shape:

```json
{
  "jobId": "a1b2c3d4-...",
  "fromDate": "2021-08-09",
  "toDate": "2026-08-09",
  "status": "RUNNING",
  "totalDates": 1305,
  "processedDates": 412,
  "succeeded": 400,
  "skipped": 8,
  "notFound": 4,
  "failed": 0,
  "totalRows": 963200,
  "startedAt": "2026-08-09T18:30:00Z",
  "finishedAt": null,
  "results": [
    { "tradeDate": "2021-08-09", "status": "SUCCESS", "rowCount": 2337, "message": null }
  ]
}
```

`results` is omitted from the list endpoint and included on the single-job endpoint.

### Startup reconciliation

On boot, any job left `RUNNING` is set to `INTERRUPTED` with a message recording where it
stopped. The JVM that owned it is gone, so reporting `RUNNING` would be a lie. Recovery is
an explicit re-POST of the same range — cheap, because skip logic drops everything already
loaded. No automatic resume: a restart loop must never silently generate ~87 minutes of NSE
traffic nobody asked for.

### Interaction with the daily scheduler

`BhavcopyScheduler` fires at 19:00 IST and may overlap a running backfill. Both paths write
through the same idempotent upserts, so concurrent writes to the same date are safe. The
single-thread executor serializes backfill jobs only; it does not block the scheduler. No
extra coordination.

## Testing

Following existing conventions (JUnit 5 + Mockito + AssertJ, `@WebMvcTest` for controllers,
no live HTTP).

**`BackfillServiceTest`** — `BhavcopyService` and job repositories mocked:
- weekends excluded from the candidate set
- dates already in `equity_price` recorded `SKIPPED`, never fetched
- dates previously `NOT_FOUND` recorded `SKIPPED`, never fetched
- `force=true` bypasses both skip sets
- one failing date does not stop the walk; remaining dates still processed
- `max-consecutive-failures` consecutive failures aborts the job as `FAILED`
- an intervening success resets the consecutive-failure counter
- `NOT_FOUND` does not advance the breaker

**`BhavcopyServiceTest`** (additions):
- CSV whose `DATE1` disagrees with the requested date yields `FAILURE` and writes nothing
- CSV whose `DATE1` matches persists normally (guards against a false positive)

**`BhavcopyControllerTest`** (additions):
- valid range → `202` with a `jobId`
- `from > to`, future `to`, `from` before `earliest-date` → `400`
- job already `RUNNING` → `409`
- unknown `jobId` → `404`

**`BackfillServiceIT`** (Testcontainers, opt-in, `*IT` suffix — not run by `mvn test`):
- `V2` migration applies cleanly on top of `V1`
- job and per-date rows persist and are queryable
- a second run over the same range skips every already-loaded date

## Out of scope

- Legacy pre-October-2019 `cm<DD><MON><YYYY>bhav.csv.zip` ingestion
- Parallel downloads — deliberately serialized to stay under NSE's radar
- Automatic resume on boot
- Retry of individual failed dates within a run (re-POST the range instead)
