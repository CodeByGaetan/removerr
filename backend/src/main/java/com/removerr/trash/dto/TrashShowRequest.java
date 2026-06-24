package com.removerr.trash.dto;

import jakarta.validation.constraints.Positive;

public record TrashShowRequest(@Positive int sonarrId, boolean immediate) {}
