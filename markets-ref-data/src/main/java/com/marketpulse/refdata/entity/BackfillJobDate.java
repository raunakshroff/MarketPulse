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
