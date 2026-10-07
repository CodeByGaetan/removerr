package com.removerr.integration;

/** The upstream service answered 404: the requested resource doesn't exist (anymore). */
public class ResourceNotFoundException extends IntegrationException {
    public ResourceNotFoundException(String message) { super(message); }
}
