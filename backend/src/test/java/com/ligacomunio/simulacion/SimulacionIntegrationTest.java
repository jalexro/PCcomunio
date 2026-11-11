package com.ligacomunio.simulacion;

import com.ligacomunio.IntegracionBase;
import com.ligacomunio.league.AdvanceMode;
import com.ligacomunio.league.AvanceDeJornada;
import com.ligacomunio.league.Formation;
import com.ligacomunio.league.LeagueDtos.ClasificacionDto;
import com.ligacomunio.league.LeagueDtos.LigaDto;
import com.ligacomunio.league.LeagueService;
import com.ligacomunio.league.LeagueStatus;
import com.ligacomunio.league.SquadService;
import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.player.Position;
import com.ligacomunio.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Temporada simulada de principio a fin con varios usuarios en la misma liga. Comprueba lo que de verdad
 * importa del cambio: que una jornada sin estadísticas se pueda jugar igual (simulándola), que la simulación
 * sea reproducible, que no pise los datos reales importados y que cinco jornadas seguidas con cuatro
 * usuarios dejen puntos, abonos y clasificación coherentes.
 */
class SimulacionIntegrationTest extends IntegracionBase {

	@Autowired
	private LeagueService leagueService;
	@Autowired
	private SquadService squadService;
	@Autowired
	private AvanceDeJornada avance;
	@Autowired
	private SimuladorDeJornada simulador;

	private AppUser ana;
	private AppUser beto;
	private AppUser carla;
	private AppUser dani;

	@BeforeEach
	void prepararLigaGrande() {
		ana = usuario("ana");
		beto = usuario("beto");
		carla = usuario("carla");
		dani = usuario("dani");
		// El catálogo de la base de tests da para tres plantillas; aquí hacen falta cuatro más el mercado,
		// y los dos equipos necesitan al menos 11 jugadores cada uno para que la simulación los alinee.
		ampliarCatalogo(Position.PORTERO, 10);
		ampliarCatalogo(Position.DEFENSA, 16);
		ampliarCatalogo(Position.CENTROCAMPISTA, 16);
		ampliarCatalogo(Position.DELANTERO, 12);
	}

	private void ampliarCatalogo(Position posicion, int cuantos) {
		for (int i = 1; i <= cuantos; i++) {
			jugador("extra " + posicion.name().charAt(0) + i, posicion, i % 2 == 0 ? equipoA : equipoB,
					500_000L * (i + 1));
		}
	}

	/** Calendario sin estadísticas: es lo que la simulación tiene que rellenar. */
	private List<Match> calendario(int jornadas) {
		List<Match> partidos = new ArrayList<>();
		for (int jornada = 1; jornada <= jornadas; jornada++) {
			partidos.add(partido(2024, jornada, MatchStatus.PROGRAMADO));
		}
		return partidos;
	}

	private LigaDto ligaDeCuatro() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga simulada");
		leagueService.unirse(beto.getId(), liga.joinCode());
		leagueService.unirse(carla.getId(), liga.joinCode());
		leagueService.unirse(dani.getId(), liga.joinCode());
		return liga;
	}

	private Map<String, Object> filaDeJornada(Long ligaId, String username, int jornada) {
		return jdbc.queryForMap("""
				select s.points, s.position, s.points_payout, s.position_bonus, s.empty_slots, s.empty_slot_penalty
				from member_matchday_score s
				join league_member m on m.id = s.member_id
				join app_user u on u.id = m.user_id
				where m.league_id = ? and u.username = ? and s.matchday = ?
				""", ligaId, username, jornada);
	}

	// --- La simulación en sí ---

	@Test
	void simularUnaJornadaDejaResultadoYEstadisticasConPuntos() {
		Match partido = calendario(1).get(0);

		SimuladorDeJornada.ResultadoSimulacion resultado = simulador.simularJornada(2024, 1);

		assertThat(resultado.partidos()).isEqualTo(1);
		assertThat(resultado.estadisticas()).isGreaterThanOrEqualTo(22); // los 11 de cada equipo, más los cambios
		Match guardado = matchRepository.findById(partido.getId()).orElseThrow();
		assertThat(guardado.getStatus()).isEqualTo(MatchStatus.FINALIZADO);
		assertThat(guardado.getHomeGoals()).isNotNull();
		assertThat(guardado.getAwayGoals()).isNotNull();

		List<PlayerMatchStats> stats = statsRepository.findByMatchId(partido.getId());
		assertThat(stats).allSatisfy(s -> {
			assertThat(s.getMinutes()).isPositive();
			assertThat(s.getPoints()).isNotNull();
		});
		// Los goles repartidos entre los jugadores cuadran con el marcador del partido.
		int golesEnEstadisticas = stats.stream().mapToInt(PlayerMatchStats::getGoals).sum();
		assertThat(golesEnEstadisticas).isEqualTo(guardado.getHomeGoals() + guardado.getAwayGoals());
	}

	@Test
	void laSimulacionEsReproducible() {
		Match partido = calendario(1).get(0);
		simulador.simularJornada(2024, 1);
		Map<Long, BigDecimal> primeraVez = puntosPorJugador(partido.getId());

		jdbc.update("delete from player_match_stats where match_id = ?", partido.getId());
		simulador.simularJornada(2024, 1);

		assertThat(puntosPorJugador(partido.getId())).isEqualTo(primeraVez);
	}

	@Test
	void noSobrescribeLasEstadisticasYaImportadas() {
		Match partido = partido(2024, 1, MatchStatus.FINALIZADO);
		estadisticasParaTodos(partido, "7.00");
		long filasAntes = statsRepository.count();

		SimuladorDeJornada.ResultadoSimulacion resultado = simulador.simularJornada(2024, 1);

		assertThat(resultado.partidos()).isZero();
		assertThat(statsRepository.count()).isEqualTo(filasAntes);
		assertThat(statsRepository.findByMatchId(partido.getId()))
				.allSatisfy(s -> assertThat(s.getPoints()).isEqualByComparingTo("7.00"));
	}

	private Map<Long, BigDecimal> puntosPorJugador(Long partidoId) {
		Map<Long, BigDecimal> puntos = new java.util.HashMap<>();
		statsRepository.findByMatchId(partidoId).forEach(s -> puntos.put(s.getPlayer().getId(), s.getPoints()));
		return puntos;
	}

	// --- Cinco jornadas con cuatro usuarios ---

	@Test
	void cincoJornadasSeguidasConCuatroUsuarios() {
		calendario(6); // 6 para que la liga siga en curso tras la quinta
		LigaDto liga = ligaDeCuatro();
		List<String> usuarios = List.of("ana", "beto", "carla", "dani");

		for (int jornada = 1; jornada <= 5; jornada++) {
			avance.avanzar(liga.id(), false);
			LigaDto tras = leagueService.detalle(ana.getId(), liga.id());
			assertThat(tras.currentMatchday()).as("jornada avanzada").isEqualTo(jornada);
			assertThat(tras.status()).isEqualTo(LeagueStatus.EN_CURSO);

			// Cada jornada deja una fila por miembro, con su puesto y sin puestos vacíos (nadie ha vendido).
			for (String username : usuarios) {
				Map<String, Object> fila = filaDeJornada(liga.id(), username, jornada);
				assertThat((Integer) fila.get("position")).isBetween(1, 4);
				assertThat((Integer) fila.get("empty_slots")).isZero();
				assertThat((BigDecimal) fila.get("empty_slot_penalty")).isEqualByComparingTo("0");
			}
		}

		// Los puntos totales de cada miembro son la suma de sus cinco jornadas.
		for (String username : usuarios) {
			BigDecimal suma = jdbc.queryForObject("""
					select coalesce(sum(s.points), 0) from member_matchday_score s
					join league_member m on m.id = s.member_id
					join app_user u on u.id = m.user_id
					where m.league_id = ? and u.username = ?
					""", BigDecimal.class, liga.id(), username);
			BigDecimal total = jdbc.queryForObject("""
					select m.total_points from league_member m
					join app_user u on u.id = m.user_id where m.league_id = ? and u.username = ?
					""", BigDecimal.class, liga.id(), username);
			assertThat(total).as("totales de " + username).isEqualByComparingTo(suma);
		}

		// La clasificación sale ordenada de más a menos puntos y con los cuatro miembros.
		List<ClasificacionDto> tabla = leagueService.clasificacion(beto.getId(), liga.id());
		assertThat(tabla).hasSize(4);
		assertThat(tabla).extracting(ClasificacionDto::totalPoints).isSortedAccordingTo(java.util.Comparator.reverseOrder());
		assertThat(tabla).extracting(ClasificacionDto::username).containsExactlyInAnyOrderElementsOf(usuarios);
		// Con cinco jornadas simuladas alguien tiene que haber sumado puntos.
		assertThat(tabla.get(0).totalPoints()).isGreaterThan(BigDecimal.ZERO);
		// Y los presupuestos siguen siendo válidos (la base de datos no admite negativos, pero se comprueba igual).
		for (ClasificacionDto fila : tabla) {
			assertThat(presupuesto(fila.memberId())).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		}

		// El mercado se ha refrescado una vez por jornada jugada.
		Integer ciclo = jdbc.queryForObject("select market_cycle from league where id = ?", Integer.class, liga.id());
		assertThat(ciclo).isEqualTo(6); // uno al crear la liga y uno por cada una de las cinco jornadas
	}

	// --- Puestos vacíos ---

	@Test
	void cadaPuestoVacioDeLaAlineacionResta4Puntos() {
		calendario(2);
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de huecos");
		leagueService.unirse(beto.getId(), liga.joinCode());
		// Ana vende dos titulares: se queda con 9 y la táctica deja dos puestos sin cubrir.
		List<Long> suyos = squadService.miPlantilla(ana.getId(), liga.id()).players().stream()
				.map(j -> j.playerId()).toList();
		squadService.vender(ana.getId(), liga.id(), suyos.get(0));
		squadService.vender(ana.getId(), liga.id(), suyos.get(1));

		avance.avanzar(liga.id(), false);

		Map<String, Object> deAna = filaDeJornada(liga.id(), "ana", 1);
		assertThat((Integer) deAna.get("empty_slots")).isEqualTo(2);
		assertThat((BigDecimal) deAna.get("empty_slot_penalty")).isEqualByComparingTo("8.00");
		Map<String, Object> deBeto = filaDeJornada(liga.id(), "beto", 1);
		assertThat((Integer) deBeto.get("empty_slots")).isZero();

		// Los puntos de Ana son los de sus 9 titulares menos los 8 de penalización.
		BigDecimal puntosDeSusTitulares = jdbc.queryForObject("""
				select coalesce(sum(st.points), 0) from squad_player sp
				join league_member m on m.id = sp.member_id
				join app_user u on u.id = m.user_id
				join player_match_stats st on st.player_id = sp.player_id
				join matches p on p.id = st.match_id
				where m.league_id = ? and u.username = 'ana' and sp.starter = true and p.matchday = 1
				""", BigDecimal.class, liga.id());
		assertThat((BigDecimal) deAna.get("points"))
				.isEqualByComparingTo(puntosDeSusTitulares.subtract(new BigDecimal("8")));
	}

	// --- Modo por confirmación ---

	@Test
	void porConfirmacionLaJornadaSeJuegaEnCuantoConfirmanTodos() {
		calendario(3);
		LigaDto liga = leagueService.crear(ana.getId(), "Liga por confirmacion");
		leagueService.unirse(beto.getId(), liga.joinCode());
		leagueService.cambiarOpciones(ana.getId(), liga.id(), AdvanceMode.POR_CONFIRMACION,
				new BigDecimal("100000"), List.of(new BigDecimal("1000000")));

		// Nadie ha confirmado: el reloj no avanza nada porque la fecha límite está a tres días.
		avance.avanzar(liga.id(), true);
		assertThat(leagueService.detalle(ana.getId(), liga.id()).currentMatchday()).isZero();

		boolean todosTrasAna = squadService.confirmar(ana.getId(), liga.id());
		assertThat(todosTrasAna).isFalse();
		avance.avanzar(liga.id(), true);
		LigaDto conAnaConfirmada = leagueService.detalle(ana.getId(), liga.id());
		assertThat(conAnaConfirmada.currentMatchday()).isZero();
		assertThat(conAnaConfirmada.iConfirmed()).isTrue();
		assertThat(conAnaConfirmada.pendingConfirmations()).containsExactly("beto");

		boolean todosTrasBeto = squadService.confirmar(beto.getId(), liga.id());
		assertThat(todosTrasBeto).isTrue();
		avance.avanzar(liga.id(), true);

		LigaDto jugada = leagueService.detalle(ana.getId(), liga.id());
		assertThat(jugada.currentMatchday()).isEqualTo(1);
		// Al avanzar, las confirmaciones de la jornada anterior ya no valen para la siguiente.
		assertThat(jugada.iConfirmed()).isFalse();
		assertThat(jugada.pendingConfirmations()).containsExactlyInAnyOrder("ana", "beto");
	}

	@Test
	void cambiarLaAlineacionDeshaceLaConfirmacion() {
		calendario(2);
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de alineaciones");
		leagueService.cambiarOpciones(ana.getId(), liga.id(), AdvanceMode.POR_CONFIRMACION,
				new BigDecimal("100000"), List.of());
		squadService.confirmar(ana.getId(), liga.id());
		assertThat(leagueService.detalle(ana.getId(), liga.id()).iConfirmed()).isTrue();

		List<Long> titulares = squadService.miPlantilla(ana.getId(), liga.id()).players().stream()
				.map(j -> j.playerId()).toList();
		squadService.alinear(ana.getId(), liga.id(), Formation.F442, titulares);

		assertThat(leagueService.detalle(ana.getId(), liga.id()).iConfirmed()).isFalse();
	}
}
