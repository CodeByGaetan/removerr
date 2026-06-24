package com.removerr.trash.dto;

public record TrashItemResponse(
        long id,
        String mediaType,
        String externalService,
        int externalId,
        Integer tmdbId,
        String title,
        Integer year,
        String posterUrl,
        long sizeBytes,
        String trashedAt,
        String purgeAt,
        String seerrRequestedBy,
        Long trashedByUserId
) {}
