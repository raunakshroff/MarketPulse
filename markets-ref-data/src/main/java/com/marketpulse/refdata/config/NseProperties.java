package com.marketpulse.refdata.config;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nse")
public class NseProperties {

    private String baseUrl;
    private String archiveUrl;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Scheduler scheduler = new Scheduler();
    private Backfill backfill = new Backfill();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getArchiveUrl() {
        return archiveUrl;
    }

    public void setArchiveUrl(String archiveUrl) {
        this.archiveUrl = archiveUrl;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Backfill getBackfill() {
        return backfill;
    }

    public void setBackfill(Backfill backfill) {
        this.backfill = backfill;
    }

    public static class Scheduler {
        private boolean enabled = true;
        private String cron;
        private String zone = "Asia/Kolkata";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }

    public static class Backfill {

        /** Pause between per-date downloads. 2s is the pace validated against NSE without rate-limiting. */
        private long delayMs = 2000L;

        /** Consecutive FAILURE outcomes that abort a job, so a blocked run stops instead of grinding on. */
        private int maxConsecutiveFailures = 10;

        /**
         * Earliest date the NSE archive serves a trustworthy sec_bhavdata_full file.
         * 2019-09-30 returns HTTP 200 but contains rows dated 27-Jun-2019, so the usable floor is the 1st.
         */
        private LocalDate earliestDate = LocalDate.of(2019, 10, 1);

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getMaxConsecutiveFailures() {
            return maxConsecutiveFailures;
        }

        public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
        }

        public LocalDate getEarliestDate() {
            return earliestDate;
        }

        public void setEarliestDate(LocalDate earliestDate) {
            this.earliestDate = earliestDate;
        }
    }
}
