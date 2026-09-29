package com.removerr.auth.dto;

// Entry of GET https://plex.tv/api/v2/shared_servers/owned/accepted
public record PlexSharedServer(
        long invitedId,
        String machineIdentifier,
        String deletedAt,
        String leftAt
) {}
