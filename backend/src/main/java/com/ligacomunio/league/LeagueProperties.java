package com.ligacomunio.league;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Reglas del juego, configurables en application.properties (prefijo app.league). */
@ConfigurationProperties(prefix = "app.league")
public record LeagueProperties(
		int season,
		/** Presupuesto que se propone al crear una liga; para repartir manda el guardado en la liga. */
		BigDecimal initialBudget,
		int maxSquadSize,
		int maxMembers,
		int marketSize,
		int bidCapPercent,
		/** Puntos que se restan por cada puesto de la táctica que se quede sin jugador. */
		BigDecimal emptySlotPenalty,
		long defaultIntervalSeconds,
		long schedulerDelayMs) {
}
