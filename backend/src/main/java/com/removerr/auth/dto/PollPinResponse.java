package com.removerr.auth.dto;

public record PollPinResponse(Status status, MeResponse user) {
    public enum Status { PENDING, SUCCESS, EXPIRED }

    public static PollPinResponse pending() {
        return new PollPinResponse(Status.PENDING, null);
    }

    public static PollPinResponse expired() {
        return new PollPinResponse(Status.EXPIRED, null);
    }

    public static PollPinResponse success(MeResponse user) {
        return new PollPinResponse(Status.SUCCESS, user);
    }
}
