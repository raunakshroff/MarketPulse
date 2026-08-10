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
