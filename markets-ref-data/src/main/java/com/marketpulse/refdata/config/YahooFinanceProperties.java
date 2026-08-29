package com.marketpulse.refdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yahoo-finance")
public class YahooFinanceProperties {

    private String cookieWarmupUrl;
    private String crumbUrl;
    private String quoteSummaryUrl;

    public String getCookieWarmupUrl() {
        return cookieWarmupUrl;
    }

    public void setCookieWarmupUrl(String cookieWarmupUrl) {
        this.cookieWarmupUrl = cookieWarmupUrl;
    }

    public String getCrumbUrl() {
        return crumbUrl;
    }

    public void setCrumbUrl(String crumbUrl) {
        this.crumbUrl = crumbUrl;
    }

    public String getQuoteSummaryUrl() {
        return quoteSummaryUrl;
    }

    public void setQuoteSummaryUrl(String quoteSummaryUrl) {
        this.quoteSummaryUrl = quoteSummaryUrl;
    }
}
