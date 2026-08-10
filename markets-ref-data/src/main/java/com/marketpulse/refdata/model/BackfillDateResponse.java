package com.marketpulse.refdata.model;

import java.time.LocalDate;

/** One date's outcome within a backfill job. {@code rowCount} is a count, not the data. */
public record BackfillDateResponse(LocalDate tradeDate, BackfillDateStatus status, long rowCount, String message) {
}
