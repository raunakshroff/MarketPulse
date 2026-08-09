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
