package com.removerr.plexuser.dto;

import com.removerr.plexuser.Role;

public record PlexUserResponse(
        long id,
        Integer plexAccountId,
        String name,
        boolean owner,
        Role role,
        boolean counted,
        String lastLoginAt
) {}
