package com.ligacomunio.match;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Estadísticas brutas de un jugador en un partido. Los puntos no se envían: los calcula el backend. */
public record EstadisticasRequest(
		@NotNull Long playerId,
		@Min(0) @Max(130) int minutes,
		@Min(0) int goals,
		@Min(0) int assists,
		@Min(0) int tacklesWon,
		@Min(0) @Max(2) int yellowCards,
		@Min(0) @Max(1) int redCards,
		@Min(0) int passesCompleted,
		@Min(0) int passesFailed,
		@Min(0) int tirosFallados,
		@Min(0) int saves,
		@Min(0) int goalsConceded,
		@Min(0) int shotsFaced) {
}
