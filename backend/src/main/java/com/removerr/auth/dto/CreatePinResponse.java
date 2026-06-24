package com.removerr.auth.dto;

public record CreatePinResponse(long pinId, String code, String authUrl) {}
