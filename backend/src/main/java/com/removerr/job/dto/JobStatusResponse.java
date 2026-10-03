package com.removerr.job.dto;

public record JobStatusResponse(
        String key,
        String cron,
        boolean running,
        String nextRunAt,
        String lastRunAt,
        Boolean lastRunSucceeded
) {}
