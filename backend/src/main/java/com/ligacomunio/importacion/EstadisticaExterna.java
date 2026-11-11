package com.ligacomunio.importacion;

import com.ligacomunio.player.Position;

import java.math.BigDecimal;

/**
 * Estadísticas de un jugador en un partido, ya traducidas al vocabulario de las reglas de puntuación.
 * Los campos de portero solo vienen informados para porteros.
 */
public record EstadisticaExterna(
		long jugadorId,
		String nombre,
		String foto,
		long equipoId,
		Position posicion,
		int minutos,
		int goles,
		int asistencias,
		int entradas,
		int amarillas,
		int rojas,
		int pasesCompletados,
		int pasesFallados,
		int tirosFallados,
		int paradas,
		int golesEncajados,
		int tirosRecibidos,
		BigDecimal rating) {
}
