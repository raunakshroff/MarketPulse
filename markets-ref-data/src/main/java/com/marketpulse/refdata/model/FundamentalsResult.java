package com.marketpulse.refdata.model;

public record FundamentalsResult(String symbol, Status status, String message) {

    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILURE
    }

    public static FundamentalsResult success(String symbol) {
        return new FundamentalsResult(symbol, Status.SUCCESS, null);
    }

    public static FundamentalsResult notFound(String symbol) {
        return new FundamentalsResult(symbol, Status.NOT_FOUND, "No fundamentals found for symbol " + symbol);
    }

    public static FundamentalsResult failure(String symbol, String message) {
        return new FundamentalsResult(symbol, Status.FAILURE, message);
    }
}
