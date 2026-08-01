package com.marketpulse.refdata.client;

public class NseHttpException extends RuntimeException {

    private final int statusCode;

    public NseHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
