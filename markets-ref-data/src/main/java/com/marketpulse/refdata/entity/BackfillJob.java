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
