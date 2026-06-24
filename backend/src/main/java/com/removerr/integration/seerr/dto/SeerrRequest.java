package com.removerr.integration.seerr.dto;

import java.util.List;

public record SeerrRequest(
        int id,
        int status,
        String type,
        SeerrMedia media,
        SeerrRequestedBy requestedBy,
        List<SeerrSeasonRef> seasons,
        String createdAt
) {}
