package com.removerr.plexuser;

/** How an account reaches the Plex server, refreshed by the user-sync job. */
public enum ServerAccess {
    OWNER,
    /** Friend with an accepted library share. */
    SHARED,
    /** Plex Home member with their own plex.tv account. */
    HOME,
    /** Plex Home managed user: no credentials of their own, so they can never sign in. */
    MANAGED,
    NONE;

    public boolean canSignIn() {
        return this != NONE && this != MANAGED;
    }
}
