package com.removerr.auth;

/** The Plex account authenticated fine but has no access to this Removerr instance. */
public class LoginDeniedException extends RuntimeException {
    public LoginDeniedException(String message) {
        super(message);
    }
}
