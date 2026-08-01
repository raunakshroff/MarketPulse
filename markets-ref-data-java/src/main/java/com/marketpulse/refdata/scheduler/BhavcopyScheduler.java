package com.marketpulse.refdata.scheduler;

import com.marketpulse.refdata.service.BhavcopyService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Replaces the Python cron/Task Scheduler entry point (run_bhavcopy_job.py) with an in-process schedule. */
@Component
@ConditionalOnProperty(prefix = "nse.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BhavcopyScheduler {

    private static final Logger log = LoggerFactory.getLogger(BhavcopyScheduler.class);

    private final BhavcopyService bhavcopyService;

    public BhavcopyScheduler(BhavcopyService bhavcopyService) {
        this.bhavcopyService = bhavcopyService;
    }

    @Scheduled(cron = "${nse.scheduler.cron}", zone = "${nse.scheduler.zone}")
    public void runDailyDownload() {
        log.info("Triggering scheduled Bhavcopy download job...");
        bhavcopyService.downloadBhavcopy(LocalDate.now());
        log.info("Scheduled job execution finished.");
    }
}
