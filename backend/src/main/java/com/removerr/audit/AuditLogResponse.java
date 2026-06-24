package com.removerr.audit;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        String action,
        String targetType,
        String targetId,
        String metadataJson,
        String createdAt
) {}
