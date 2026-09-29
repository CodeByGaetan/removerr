package com.removerr.trash;

import com.removerr.job.Job;
import com.removerr.trash.dto.PurgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PurgeJob implements Job<PurgeResult> {

    private static final Logger log = LoggerFactory.getLogger(PurgeJob.class);

    private final PurgeService purgeService;
    private final String cron;

    public PurgeJob(PurgeService purgeService, @Value("${removerr.purge.hour:3}") int purgeHour) {
        this.purgeService = purgeService;
        this.cron = "0 0 " + purgeHour + " * * *";
    }

    @Override
    public String key() {
        return "purge";
    }

    @Override
    public String cron() {
        return cron;
    }

    @Override
    public PurgeResult run(Long triggeredByUserId) {
        boolean manual = triggeredByUserId != null;
        log.info("{} purge triggered", manual ? "Manual" : "Scheduled");
        PurgeResult result = purgeService.purge(manual, triggeredByUserId);
        log.info("Purge done — purged={}, failed={}", result.purged(), result.failed());
        return result;
    }
}
