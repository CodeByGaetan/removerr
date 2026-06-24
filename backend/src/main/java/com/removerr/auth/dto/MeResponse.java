package com.removerr.auth.dto;

public record MeResponse(long id, String username, String email, boolean admin, int countedUsersTotal) {}
