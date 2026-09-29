package com.removerr.job.dto;

public record JobStatusResponse(
        String key,
        boolean running,
        String nextRunAt,
        String lastRunAt,
        Boolean lastRunSucceeded
) {}
