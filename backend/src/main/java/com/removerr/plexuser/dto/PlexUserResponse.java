package com.removerr.plexuser.dto;

import com.removerr.plexuser.Role;
import com.removerr.plexuser.ServerAccess;

public record PlexUserResponse(
        long id,
        Integer plexAccountId,
        String name,
        boolean owner,
        ServerAccess serverAccess,
        Role role,
        boolean counted,
        String lastLoginAt
) {}
