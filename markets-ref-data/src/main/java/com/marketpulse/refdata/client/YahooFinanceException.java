package com.marketpulse.refdata.client;

public class YahooFinanceException extends RuntimeException {

    private final int statusCode;

    public YahooFinanceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
