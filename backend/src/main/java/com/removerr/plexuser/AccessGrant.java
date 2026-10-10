package com.removerr.plexuser;

/** How a plex.tv account reaches the server, with the name plex.tv knows it by. */
public record AccessGrant(ServerAccess access, String name) {}
