package com.removerr.plexuser.dto;

import com.removerr.plexuser.Role;

/** Partial update: null fields are left unchanged. */
public record UpdateUserRequest(Boolean counted, Role role) {}
