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
