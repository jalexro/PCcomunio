package com.ligacomunio.importacion;

import com.ligacomunio.match.MatchStatus;

import java.time.Instant;

public record PartidoExterno(
		long id,
		int jornada,
		Instant fecha,
		long localId,
		long visitanteId,
		Integer golesLocal,
		Integer golesVisitante,
		MatchStatus estado) {
}
