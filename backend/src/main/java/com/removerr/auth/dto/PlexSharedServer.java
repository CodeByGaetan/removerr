package com.removerr.auth.dto;

// Entry of GET https://plex.tv/api/v2/shared_servers/owned/accepted
public record PlexSharedServer(
        long invitedId,
        String machineIdentifier,
        String deletedAt,
        String leftAt,
        Invited invited
) {
    /** The invited account; its title is the name the Plex server also shows for it. */
    public record Invited(String title) {}

    public String invitedName() {
        return invited != null ? invited.title() : null;
    }
}
