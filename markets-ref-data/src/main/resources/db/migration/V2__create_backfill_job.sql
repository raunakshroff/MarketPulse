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
