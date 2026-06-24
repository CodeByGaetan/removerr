package com.removerr.trash;

import com.removerr.trash.dto.PurgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeScheduler.class);

    private final PurgeService purgeService;

    public PurgeScheduler(PurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @Scheduled(cron = "0 0 ${removerr.purge.hour:3} * * *")
    public void scheduledPurge() {
        log.info("Scheduled purge triggered");
        PurgeResult result = purgeService.purge(false, null);
        log.info("Scheduled purge done — purged={}, failed={}", result.purged(), result.failed());
    }
}
