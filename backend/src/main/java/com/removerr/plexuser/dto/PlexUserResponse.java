package com.removerr.plexuser.dto;

public record PlexUserResponse(
        long id,
        Integer plexAccountId,
        String name,
        boolean admin,
        boolean counted
) {}
