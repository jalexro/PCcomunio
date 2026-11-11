package com.ligacomunio.match;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ResultadoRequest(
		@NotNull @Min(0) Integer homeGoals,
		@NotNull @Min(0) Integer awayGoals) {
}
