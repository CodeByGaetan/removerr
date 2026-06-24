package com.removerr.integration.sonarr.dto;

import java.util.List;

public record SonarrQueuePage(
        int page,
        int pageSize,
        int totalRecords,
        List<SonarrQueueItem> records
) {}
