package com.ligacomunio.importacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.player.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiFootballMapperTest {

	private final ObjectMapper json = new ObjectMapper();

	private JsonNode leer(String texto) throws Exception {
		return json.readTree(texto);
	}

	@Test
	void traduceUnPartidoFinalizado() throws Exception {
		JsonNode r = leer("""
				{"fixture": {"id": 1208494, "date": "2024-08-15T19:30:00+00:00", "status": {"short": "FT"}},
				 "league": {"round": "Regular Season - 1"},
				 "teams": {"home": {"id": 531}, "away": {"id": 546}},
				 "goals": {"home": 1, "away": 1}}
				""");

		PartidoExterno p = ApiFootballMapper.partido(r);

		assertThat(p.id()).isEqualTo(1208494L);
		assertThat(p.jornada()).isEqualTo(1);
		assertThat(p.fecha()).isEqualTo(Instant.parse("2024-08-15T19:30:00Z"));
		assertThat(p.localId()).isEqualTo(531L);
		assertThat(p.visitanteId()).isEqualTo(546L);
		assertThat(p.golesLocal()).isEqualTo(1);
		assertThat(p.estado()).isEqualTo(MatchStatus.FINALIZADO);
	}

	@Test
	void unPartidoSinJugarNoTieneGoles() throws Exception {
		JsonNode r = leer("""
				{"fixture": {"id": 5, "date": "2025-05-20T19:00:00+00:00", "status": {"short": "NS"}},
				 "league": {"round": "Regular Season - 38"},
				 "teams": {"home": {"id": 1}, "away": {"id": 2}},
				 "goals": {"home": null, "away": null}}
				""");

		PartidoExterno p = ApiFootballMapper.partido(r);

		assertThat(p.golesLocal()).isNull();
		assertThat(p.estado()).isEqualTo(MatchStatus.PROGRAMADO);
		assertThat(p.jornada()).isEqualTo(38);
	}

	@Test
	void rondasQueNoSonJornadasValenCero() {
		assertThat(ApiFootballMapper.jornada("Relegation Round")).isZero();
	}

	@Test
	void traduceEquipoConYSinCodigo() throws Exception {
		EquipoExterno con = ApiFootballMapper.equipo(leer("""
				{"team": {"id": 531, "name": "Athletic Club", "code": "ATH", "logo": "http://x/531.png"}}
				"""));
		EquipoExterno sin = ApiFootballMapper.equipo(leer("""
				{"team": {"id": 9, "name": "Equipo Raro", "code": null, "logo": null}}
				"""));

		assertThat(con.codigo()).isEqualTo("ATH");
		assertThat(sin.codigo()).isNull();
		assertThat(sin.escudo()).isNull();
	}

	@Test
	void traduceEstadisticasDePorteroYDelantero() throws Exception {
		JsonNode respuesta = leer("""
				[{"team": {"id": 531}, "players": [
				  {"player": {"id": 100, "name": "Alex Padilla", "photo": "http://x/100.png"},
				   "statistics": [{
				     "games": {"minutes": 90, "position": "G", "rating": "6.7"},
				     "shots": {"total": null, "on": null},
				     "goals": {"total": null, "conceded": 1, "assists": 0, "saves": 1},
				     "passes": {"total": 36, "key": null, "accuracy": "19"},
				     "tackles": {"total": null},
				     "cards": {"yellow": 0, "red": 0}}]},
				  {"player": {"id": 200, "name": "Gorka Guruzeta", "photo": null},
				   "statistics": [{
				     "games": {"minutes": 90, "position": "F", "rating": "6.9"},
				     "shots": {"total": 3, "on": 2},
				     "goals": {"total": 1, "conceded": 0, "assists": 1, "saves": null},
				     "passes": {"total": 21, "key": 2, "accuracy": "16"},
				     "tackles": {"total": 2},
				     "cards": {"yellow": 1, "red": 0}}]}
				]}]
				""");

		List<EstadisticaExterna> lista = ApiFootballMapper.estadisticas(respuesta);

		assertThat(lista).hasSize(2);

		EstadisticaExterna portero = lista.get(0);
		assertThat(portero.posicion()).isEqualTo(Position.PORTERO);
		assertThat(portero.equipoId()).isEqualTo(531L);
		assertThat(portero.paradas()).isEqualTo(1);
		assertThat(portero.golesEncajados()).isEqualTo(1);
		assertThat(portero.tirosRecibidos()).isEqualTo(2); // paradas + goles encajados
		assertThat(portero.pasesCompletados()).isEqualTo(19);
		assertThat(portero.pasesFallados()).isEqualTo(17); // 36 - 19
		assertThat(portero.rating()).isEqualByComparingTo(new BigDecimal("6.7"));

		EstadisticaExterna delantero = lista.get(1);
		assertThat(delantero.posicion()).isEqualTo(Position.DELANTERO);
		assertThat(delantero.goles()).isEqualTo(1);
		assertThat(delantero.asistencias()).isEqualTo(1);
		assertThat(delantero.entradas()).isEqualTo(2);
		assertThat(delantero.amarillas()).isEqualTo(1);
		assertThat(delantero.tirosFallados()).isEqualTo(2); // 3 tiros - 1 gol (opción B)
		assertThat(delantero.paradas()).isZero(); // los campos de portero solo cuentan en porteros
		assertThat(delantero.foto()).isNull();
	}

	@Test
	void sustitutoQueNoJugoTraeMinutosCeroYPosicionDesconocidaSeOmite() throws Exception {
		JsonNode respuesta = leer("""
				[{"team": {"id": 531}, "players": [
				  {"player": {"id": 300, "name": "Suplente", "photo": null},
				   "statistics": [{"games": {"minutes": null, "position": "M", "rating": null},
				     "shots": {"total": null}, "goals": {"total": null}, "passes": {"total": null, "accuracy": null},
				     "tackles": {"total": null}, "cards": {"yellow": 0, "red": 0}}]},
				  {"player": {"id": 301, "name": "Sin posicion", "photo": null},
				   "statistics": [{"games": {"minutes": 45, "position": null}}]}
				]}]
				""");

		List<EstadisticaExterna> lista = ApiFootballMapper.estadisticas(respuesta);

		assertThat(lista).hasSize(1);
		assertThat(lista.get(0).minutos()).isZero();
		assertThat(lista.get(0).rating()).isNull();
	}
}
