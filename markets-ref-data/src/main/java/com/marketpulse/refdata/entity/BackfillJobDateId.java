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
