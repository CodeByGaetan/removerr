package com.removerr.setting.dto;

public record ConnectionTestResponse(
        ServiceStatus plex,
        ServiceStatus radarr,
        ServiceStatus sonarr,
        ServiceStatus seerr
) {
    public record ServiceStatus(Status status, String error) {
        public static ServiceStatus ok() {
            return new ServiceStatus(Status.OK, null);
        }

        public static ServiceStatus notConfigured() {
            return new ServiceStatus(Status.NOT_CONFIGURED, null);
        }

        public static ServiceStatus authFailed() {
            return new ServiceStatus(Status.AUTH_FAILED, "Invalid credentials");
        }

        public static ServiceStatus unreachable(String error) {
            return new ServiceStatus(Status.UNREACHABLE, error);
        }
    }

    public enum Status { OK, NOT_CONFIGURED, AUTH_FAILED, UNREACHABLE }
}
