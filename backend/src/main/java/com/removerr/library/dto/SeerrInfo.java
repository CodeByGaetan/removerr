package com.removerr.library.dto;

public record SeerrInfo(
        int requestId,
        String requestedByUsername,
        String requestedByAvatar,
        String requestedAt
) {}
