package com.removerr.integration;

public class ServiceNotConfiguredException extends IntegrationException {
    public ServiceNotConfiguredException(String settingKey) {
        super("Service not configured — missing setting: " + settingKey);
    }
}
