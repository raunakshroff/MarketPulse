package com.marketpulse.refdata.model;

import java.time.LocalDate;

public record DownloadResult(LocalDate date, Status status, String location, long rowCount, String message) {

    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILURE
    }

    public static DownloadResult success(LocalDate date, String location, long rowCount) {
        return new DownloadResult(date, Status.SUCCESS, location, rowCount, null);
    }

    public static DownloadResult notFound(LocalDate date) {
        return new DownloadResult(date, Status.NOT_FOUND, null, 0,
                "Bhavcopy not published for this date (market holiday or not yet available)");
    }

    public static DownloadResult failure(LocalDate date, String message) {
        return new DownloadResult(date, Status.FAILURE, null, 0, message);
    }
}
