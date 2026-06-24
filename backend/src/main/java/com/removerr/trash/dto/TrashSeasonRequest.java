package com.removerr.trash.dto;

import jakarta.validation.constraints.Positive;

public record TrashSeasonRequest(@Positive int sonarrId, int seasonNumber, boolean immediate) {}
