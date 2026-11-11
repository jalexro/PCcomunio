package com.ligacomunio.match;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CrearPartidoRequest(
		@Min(1) int matchday,
		@NotNull Instant kickoff,
		@NotNull Long homeTeamId,
		@NotNull Long awayTeamId) {
}
