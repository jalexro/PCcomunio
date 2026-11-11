package com.ligacomunio.importacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.player.Position;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traduce las respuestas JSON de API-Football (v3) a los tipos neutros de la importación. */
final class ApiFootballMapper {

	private static final Pattern NUMERO_FINAL = Pattern.compile("(\\d+)\\s*$");

	private ApiFootballMapper() {
	}

	static EquipoExterno equipo(JsonNode r) {
		JsonNode t = r.path("team");
		return new EquipoExterno(t.path("id").asLong(), t.path("name").asText(), texto(t, "code"), texto(t, "logo"));
	}

	static PartidoExterno partido(JsonNode r) {
		JsonNode f = r.path("fixture");
		Instant fecha = OffsetDateTime.parse(f.path("date").asText()).toInstant();
		return new PartidoExterno(
				f.path("id").asLong(),
				jornada(r.path("league").path("round").asText()),
				fecha,
				r.path("teams").path("home").path("id").asLong(),
				r.path("teams").path("away").path("id").asLong(),
				enteroONulo(r.path("goals").path("home")),
				enteroONulo(r.path("goals").path("away")),
				estado(f.path("status").path("short").asText()));
	}

	/** "Regular Season - 12" -> 12. Devuelve 0 si la ronda no es numerada. */
	static int jornada(String ronda) {
		Matcher m = NUMERO_FINAL.matcher(ronda);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	static MatchStatus estado(String corto) {
		return switch (corto) {
			case "FT", "AET", "PEN" -> MatchStatus.FINALIZADO;
			case "1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT" -> MatchStatus.EN_JUEGO;
			default -> MatchStatus.PROGRAMADO;
		};
	}

	/** Respuesta de /fixtures/players: un elemento por equipo con su lista de jugadores. */
	static List<EstadisticaExterna> estadisticas(JsonNode response) {
		List<EstadisticaExterna> resultado = new ArrayList<>();
		for (JsonNode equipo : response) {
			long equipoId = equipo.path("team").path("id").asLong();
			for (JsonNode jugador : equipo.path("players")) {
				JsonNode estadisticas = jugador.path("statistics");
				if (estadisticas.isEmpty()) continue;
				EstadisticaExterna e = estadistica(equipoId, jugador.path("player"), estadisticas.get(0));
				if (e != null) resultado.add(e);
			}
		}
		return resultado;
	}

	private static EstadisticaExterna estadistica(long equipoId, JsonNode jugador, JsonNode s) {
		Position posicion = posicion(s.path("games").path("position").asText());
		if (posicion == null) return null;

		int goles = entero(s.path("goals").path("total"));
		int paradas = entero(s.path("goals").path("saves"));
		int encajados = entero(s.path("goals").path("conceded"));
		int pasesTotales = entero(s.path("passes").path("total"));
		// En esta API "accuracy" es el número de pases acertados, no un porcentaje.
		int pasesCompletados = Math.min(entero(s.path("passes").path("accuracy")), pasesTotales);
		boolean portero = posicion == Position.PORTERO;

		return new EstadisticaExterna(
				jugador.path("id").asLong(),
				jugador.path("name").asText(),
				texto(jugador, "photo"),
				equipoId,
				posicion,
				entero(s.path("games").path("minutes")),
				goles,
				entero(s.path("goals").path("assists")),
				entero(s.path("tackles").path("total")),
				entero(s.path("cards").path("yellow")),
				entero(s.path("cards").path("red")),
				pasesCompletados,
				pasesTotales - pasesCompletados,
				// Tiro fallado = todo tiro que no acabó en gol (incluye los parados).
				Math.max(0, entero(s.path("shots").path("total")) - goles),
				portero ? paradas : 0,
				portero ? encajados : 0,
				// La API no da los tiros recibidos: se aproximan con paradas + goles encajados.
				portero ? paradas + encajados : 0,
				decimal(s.path("games").path("rating")));
	}

	/**
	 * Respuesta de /players: un elemento por jugador con una entrada de "statistics" por cada equipo en el
	 * que ha jugado esa temporada. Se toma la del equipo en el que más partidos ha disputado, que es el que
	 * de verdad representa; si no hay ninguna posición reconocible se devuelve null y el jugador se omite.
	 */
	static JugadorExterno jugadorDePlantilla(JsonNode r) {
		JsonNode jugador = r.path("player");
		JsonNode mejor = null;
		int masPartidos = -1;
		for (JsonNode s : r.path("statistics")) {
			if (posicion(s.path("games").path("position").asText()) == null) {
				continue;
			}
			int partidos = entero(s.path("games").path("appearences"));
			if (partidos > masPartidos) {
				masPartidos = partidos;
				mejor = s;
			}
		}
		if (mejor == null) {
			return null;
		}
		return new JugadorExterno(
				jugador.path("id").asLong(),
				jugador.path("name").asText(),
				texto(jugador, "photo"),
				mejor.path("team").path("id").asLong(),
				posicion(mejor.path("games").path("position").asText()));
	}

	private static Position posicion(String letra) {
		return switch (letra) {
			case "G" -> Position.PORTERO;
			case "D" -> Position.DEFENSA;
			case "M" -> Position.CENTROCAMPISTA;
			case "F" -> Position.DELANTERO;
			default -> null;
		};
	}

	/** Los campos sin dato llegan como null o como texto ("16"): se leen como 0 si faltan. */
	static int entero(JsonNode n) {
		if (n.isNumber()) return n.asInt();
		if (n.isTextual()) {
			String digitos = n.asText().replaceAll("[^0-9]", "");
			return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
		}
		return 0;
	}

	private static Integer enteroONulo(JsonNode n) {
		return n.isNumber() ? n.asInt() : null;
	}

	private static BigDecimal decimal(JsonNode n) {
		if (n.isNumber()) return n.decimalValue();
		if (n.isTextual() && !n.asText().isBlank()) {
			try {
				return new BigDecimal(n.asText().trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static String texto(JsonNode n, String campo) {
		JsonNode v = n.path(campo);
		return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
	}
}
