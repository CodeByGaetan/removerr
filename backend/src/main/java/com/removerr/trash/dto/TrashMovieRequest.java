package com.removerr.trash.dto;

import jakarta.validation.constraints.Positive;

public record TrashMovieRequest(@Positive int radarrId, boolean immediate) {}
