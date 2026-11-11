package com.ligacomunio.league;

import com.ligacomunio.IntegracionBase;
import com.ligacomunio.league.LeagueDtos.AbonoDto;
import com.ligacomunio.league.LeagueDtos.ClasificacionDto;
import com.ligacomunio.league.LeagueDtos.CrearLigaRequest;
import com.ligacomunio.league.LeagueDtos.JugadorMercadoDto;
import com.ligacomunio.league.LeagueDtos.JugadorPlantillaDto;
import com.ligacomunio.league.LeagueDtos.LigaDto;
import com.ligacomunio.league.LeagueDtos.OfertaDto;
import com.ligacomunio.league.LeagueDtos.PlantillaDto;
import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import com.ligacomunio.user.AppUser;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

class LigaIntegrationTest extends IntegracionBase {

	@Autowired
	private LeagueService leagueService;
	@Autowired
	private SquadService squadService;
	@Autowired
	private MarketService marketService;
	@Autowired
	private MatchdayService matchdayService;
	@Autowired
	private LeagueScheduler scheduler;

	private AppUser ana;
	private AppUser beto;
	private AppUser carla;

	@BeforeEach
	void crearUsuarios() {
		ana = usuario("ana");
		beto = usuario("beto");
		carla = usuario("carla");
	}

	// --- Ayudas ---

	private void esperaEstado(HttpStatus estado, ThrowingCallable accion) {
		assertThatThrownBy(accion).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).as(e.getReason()).isEqualTo(estado));
	}

	private void esperaMotivo(HttpStatus estado, String fragmento, ThrowingCallable accion) {
		assertThatThrownBy(accion).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(estado);
			assertThat(e.getReason()).contains(fragmento);
		});
	}

	/** Liga sin abonos (valor del punto 0 y sin premios) para que los presupuestos solo cambien por el mercado. */
	private LigaDto ligaDeAnaConBeto() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		jdbc.update("update league set point_value = 0, position_bonuses = '' where id = ?", liga.id());
		return liga;
	}

	/** Liga con los abonos por defecto (100.000 por punto y premios 3 M, 2 M y 1 M). */
	private LigaDto ligaConAbonosDeAnaConBeto() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de prueba");
		leagueService.unirse(beto.getId(), liga.joinCode());
		return liga;
	}

	/** Sube a 9 los puntos de un titular de Ana: tras la jornada 1 Ana suma 19 y Beto 11. */
	private Match jornadaDondeGanaAna(LigaDto liga) {
		Match jornada1 = jornadaCompleta(1, "1.00");
		Long estrella = squadService.miPlantilla(ana.getId(), liga.id()).players().get(0).playerId();
		jdbc.update("update player_match_stats set points = 9.00 where player_id = ? and match_id = ?", estrella, jornada1.getId());
		return jornada1;
	}

	private Set<Long> ids(PlantillaDto plantilla) {
		return plantilla.players().stream().map(JugadorPlantillaDto::playerId).collect(Collectors.toSet());
	}

	private Long miembroId(Long userId, Long ligaId) {
		return leagueService.detalle(userId, ligaId).myMemberId();
	}

	private JugadorMercadoDto primeroDelMercado(Long userId, Long ligaId) {
		return marketService.mercado(userId, ligaId).get(0);
	}

	private OfertaDto oferta(Long userId, Long ligaId, Long playerId) {
		return marketService.ofertas(userId, ligaId).stream().filter(o -> o.playerId().equals(playerId)).findFirst().orElseThrow();
	}

	private void anadirABanquillo(Long ligaId, Long memberId, Long playerId) {
		jdbc.update("insert into squad_player(league_id, member_id, player_id, starter, price_paid) values (?, ?, ?, false, 0)",
				ligaId, memberId, playerId);
	}

	/** Jugadores del catálogo sin dueño en la liga ni en su mercado actual. */
	private List<Long> libres(Long ligaId) {
		return jdbc.queryForList("""
				select id from player
				where id not in (select player_id from squad_player where league_id = ?)
				  and id not in (select player_id from market_listing where league_id = ?)
				order by id
				""", Long.class, ligaId, ligaId);
	}

	private Match jornadaCompleta(int jornada, String puntos) {
		Match partido = partido(2024, jornada, MatchStatus.FINALIZADO);
		estadisticasParaTodos(partido, puntos);
		return partido;
	}

	// --- Crear y unirse ---

	@Test
	void crearUnaLigaDaUnaPlantillaInicialAleatoriaValidaYUnMercado() {
		LigaDto liga = leagueService.crear(ana.getId(), "  Liga de prueba  ");

		assertThat(liga.name()).isEqualTo("Liga de prueba");
		assertThat(liga.joinCode()).hasSize(8);
		assertThat(liga.currentMatchday()).isZero();
		assertThat(liga.isOwner()).isTrue();
		assertThat(liga.members()).isEqualTo(1);
		assertThat(liga.myBudget()).isEqualByComparingTo("40000000");
		assertThat(liga.myFormation()).isEqualTo(Formation.F442);
		assertThat(liga.intervalSeconds()).isEqualTo(259200);

		PlantillaDto plantilla = squadService.miPlantilla(ana.getId(), liga.id());
		assertThat(plantilla.players()).hasSize(11).allMatch(JugadorPlantillaDto::starter);
		Map<Position, Long> composicion = plantilla.players().stream()
				.collect(Collectors.groupingBy(JugadorPlantillaDto::position, () -> new EnumMap<>(Position.class), Collectors.counting()));
		assertThat(composicion).containsEntry(Position.PORTERO, 1L).containsEntry(Position.DEFENSA, 4L)
				.containsEntry(Position.CENTROCAMPISTA, 4L).containsEntry(Position.DELANTERO, 2L);

		List<JugadorMercadoDto> mercado = marketService.mercado(ana.getId(), liga.id());
		assertThat(mercado).hasSize(10);
		assertThat(mercado).extracting(JugadorMercadoDto::playerId).doesNotContainAnyElementsOf(ids(plantilla));
		mercado.forEach(j -> {
			assertThat(j.minBid()).isEqualByComparingTo(j.value());
			assertThat(j.maxBid()).isEqualByComparingTo(j.value().multiply(new BigDecimal("1.2")));
		});
	}

	@Test
	void sinJugadoresConValorNoSePuedeCrearUnaLiga() {
		jdbc.update("update player set market_value = 0");

		esperaMotivo(HttpStatus.CONFLICT, "No hay jugadores con valor", () -> leagueService.crear(ana.getId(), "Liga vacía"));
	}

	@Test
	void unirseConElCodigoReparteJugadoresDistintos() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de prueba");

		LigaDto deBeto = leagueService.unirse(beto.getId(), "  " + liga.joinCode().toLowerCase() + " ");

		assertThat(deBeto.id()).isEqualTo(liga.id());
		assertThat(deBeto.members()).isEqualTo(2);
		assertThat(deBeto.isOwner()).isFalse();
		assertThat(deBeto.myBudget()).isEqualByComparingTo("40000000");
		Set<Long> deAna = ids(squadService.miPlantilla(ana.getId(), liga.id()));
		Set<Long> deBetoIds = ids(squadService.miPlantilla(beto.getId(), liga.id()));
		assertThat(deBetoIds).hasSize(11).doesNotContainAnyElementsOf(deAna);
		assertThat(leagueService.misLigas(beto.getId())).hasSize(1);
	}

	@Test
	void noSePuedeRepetirNiUsarUnCodigoFalso() {
		LigaDto liga = ligaDeAnaConBeto();

		esperaMotivo(HttpStatus.CONFLICT, "Ya estás", () -> leagueService.unirse(beto.getId(), liga.joinCode()));
		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.unirse(carla.getId(), "NOEXISTE"));
	}

	@Test
	void unaLigaYaEmpezadaNoAdmiteMasJugadores() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		matchdayService.avanzar(liga.id(), false);

		esperaMotivo(HttpStatus.CONFLICT, "ya ha empezado", () -> leagueService.unirse(carla.getId(), liga.joinCode()));
	}

	// --- Modalidades de reparto inicial ---

	@Test
	void sinJugadoresDejaLaPlantillaVaciaConElPresupuestoElegidoTambienAQuienSeUne() {
		LigaDto liga = leagueService.crear(ana.getId(), new CrearLigaRequest("Liga desde cero",
				InitialSquadMode.SIN_JUGADORES, new BigDecimal("60000000"), null));

		assertThat(liga.initialSquadMode()).isEqualTo(InitialSquadMode.SIN_JUGADORES);
		assertThat(liga.initialBudget()).isEqualByComparingTo("60000000");
		assertThat(liga.initialSquadCap()).isNull();
		assertThat(liga.myBudget()).isEqualByComparingTo("60000000");
		assertThat(squadService.miPlantilla(ana.getId(), liga.id()).players()).isEmpty();
		// El mercado se crea igual: es por donde hay que fichar.
		assertThat(marketService.mercado(ana.getId(), liga.id())).hasSize(10);

		LigaDto deBeto = leagueService.unirse(beto.getId(), liga.joinCode());

		assertThat(deBeto.myBudget()).isEqualByComparingTo("60000000");
		assertThat(squadService.miPlantilla(beto.getId(), liga.id()).players()).isEmpty();
	}

	@Test
	void conTopeLaPlantillaInicialNoPasaDelTopeNiParaElDuenoNiParaQuienSeUne() {
		// Valores de 1 M a 3 M: así cualquier once cabe de sobra en el tope y el test no depende del azar.
		jdbc.update("update player set market_value = 1000000 + (id % 5) * 500000");

		LigaDto liga = leagueService.crear(ana.getId(), new CrearLigaRequest("Liga con tope",
				InitialSquadMode.ALEATORIO_CON_TOPE, new BigDecimal("15000000"), new BigDecimal("20000000")));
		LigaDto deBeto = leagueService.unirse(beto.getId(), liga.joinCode());

		assertThat(liga.initialSquadMode()).isEqualTo(InitialSquadMode.ALEATORIO_CON_TOPE);
		assertThat(liga.initialSquadCap()).isEqualByComparingTo("20000000");
		assertThat(liga.myBudget()).isEqualByComparingTo("15000000");
		assertThat(deBeto.myBudget()).isEqualByComparingTo("15000000");

		for (Long userId : List.of(ana.getId(), beto.getId())) {
			PlantillaDto plantilla = squadService.miPlantilla(userId, liga.id());
			assertThat(plantilla.players()).hasSize(11).allMatch(JugadorPlantillaDto::starter);
			Map<Position, Long> composicion = plantilla.players().stream().collect(Collectors.groupingBy(
					JugadorPlantillaDto::position, () -> new EnumMap<>(Position.class), Collectors.counting()));
			assertThat(composicion).containsEntry(Position.PORTERO, 1L).containsEntry(Position.DEFENSA, 4L)
					.containsEntry(Position.CENTROCAMPISTA, 4L).containsEntry(Position.DELANTERO, 2L);
			BigDecimal suma = plantilla.players().stream().map(JugadorPlantillaDto::value)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(suma).isLessThanOrEqualTo(new BigDecimal("20000000"));
		}
	}

	@Test
	void conUnTopeImposibleNoSePuedeCrearLaLiga() {
		esperaMotivo(HttpStatus.CONFLICT, "dentro del tope", () -> leagueService.crear(ana.getId(),
				new CrearLigaRequest("Liga tacaña", InitialSquadMode.ALEATORIO_CON_TOPE,
						new BigDecimal("40000000"), new BigDecimal("1000000"))));
	}

	@Test
	void elTopeSoloSeAdmiteEnLaModalidadConTope() {
		esperaMotivo(HttpStatus.BAD_REQUEST, "solo se puede fijar", () -> leagueService.crear(ana.getId(),
				new CrearLigaRequest("Liga rara", InitialSquadMode.ALEATORIO, new BigDecimal("40000000"),
						new BigDecimal("30000000"))));
		esperaMotivo(HttpStatus.BAD_REQUEST, "Hay que fijar el tope", () -> leagueService.crear(ana.getId(),
				new CrearLigaRequest("Liga rara", InitialSquadMode.ALEATORIO_CON_TOPE, new BigDecimal("40000000"), null)));
		assertThat(leagueService.misLigas(ana.getId())).isEmpty();
	}

	/**
	 * Consecuencia conocida de empezar sin jugadores: quien llega a la jornada con los 11 puestos vacíos se
	 * come la penalización de app.league.empty-slot-penalty por cada uno (−4 x 11 = −44) y, al no sumar puntos
	 * positivos, no cobra nada.
	 */
	@Test
	void sinJugadoresLosOncePuestosVaciosRestanCuarentaYCuatroPuntos() {
		LigaDto liga = leagueService.crear(ana.getId(), new CrearLigaRequest("Liga desde cero",
				InitialSquadMode.SIN_JUGADORES, new BigDecimal("40000000"), null));
		leagueService.unirse(beto.getId(), liga.joinCode());
		jornadaCompleta(1, "5.00");

		matchdayService.avanzar(liga.id(), false);

		List<ClasificacionDto> tabla = leagueService.clasificacion(ana.getId(), liga.id());
		assertThat(tabla).hasSize(2).allSatisfy(c -> {
			assertThat(c.totalPoints()).isEqualByComparingTo("-44.00");
			assertThat(c.lastMatchdayEmptySlots()).isEqualTo(11);
		});
		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo("40000000");
	}

	@Test
	void unaLigaNoEsVisibleParaQuienNoEsMiembro() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga privada");

		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.detalle(carla.getId(), liga.id()));
		esperaEstado(HttpStatus.NOT_FOUND, () -> squadService.miPlantilla(carla.getId(), liga.id()));
		esperaEstado(HttpStatus.NOT_FOUND, () -> marketService.mercado(carla.getId(), liga.id()));
		assertThat(leagueService.misLigas(carla.getId())).isEmpty();
	}

	// --- Alineación y tácticas ---

	@Test
	void lasAlineacionesInvalidasSeRechazanConUnMotivo() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de prueba");
		PlantillaDto plantilla = squadService.miPlantilla(ana.getId(), liga.id());
		List<Long> once = plantilla.players().stream().map(JugadorPlantillaDto::playerId).toList();

		assertThat(squadService.alinear(ana.getId(), liga.id(), Formation.F442, once).formation()).isEqualTo(Formation.F442);
		esperaMotivo(HttpStatus.BAD_REQUEST, "4-3-3", () -> squadService.alinear(ana.getId(), liga.id(), Formation.F433, once));
		esperaMotivo(HttpStatus.BAD_REQUEST, "exactamente 11", () -> squadService.alinear(ana.getId(), liga.id(), Formation.F442, once.subList(0, 10)));

		List<Long> repetido = new ArrayList<>(once.subList(0, 10));
		repetido.add(once.get(0));
		esperaMotivo(HttpStatus.BAD_REQUEST, "repetidos", () -> squadService.alinear(ana.getId(), liga.id(), Formation.F442, repetido));

		List<Long> ajeno = new ArrayList<>(once.subList(0, 10));
		ajeno.add(libres(liga.id()).get(0));
		esperaMotivo(HttpStatus.BAD_REQUEST, "tu plantilla", () -> squadService.alinear(ana.getId(), liga.id(), Formation.F442, ajeno));
	}

	@Test
	void conBanquilloSePuedeCambiarDeTactica() {
		LigaDto liga = leagueService.crear(ana.getId(), "Liga de prueba");
		Long anaMiembro = miembroId(ana.getId(), liga.id());
		PlantillaDto antes = squadService.miPlantilla(ana.getId(), liga.id());
		// Un delantero más en el banquillo permite el 4-3-3 dejando fuera a un centrocampista
		Long delanteroNuevo = catalogo.stream().filter(j -> j.getPosition() == Position.DELANTERO)
				.map(Player::getId).filter(libres(liga.id())::contains).findFirst().orElseThrow();
		anadirABanquillo(liga.id(), anaMiembro, delanteroNuevo);

		List<Long> titulares = new ArrayList<>();
		titulares.add(idDePosicion(antes, Position.PORTERO, 1).get(0));
		titulares.addAll(idDePosicion(antes, Position.DEFENSA, 4));
		titulares.addAll(idDePosicion(antes, Position.CENTROCAMPISTA, 3));
		titulares.addAll(idDePosicion(antes, Position.DELANTERO, 2));
		titulares.add(delanteroNuevo);

		PlantillaDto despues = squadService.alinear(ana.getId(), liga.id(), Formation.F433, titulares);

		assertThat(despues.formation()).isEqualTo(Formation.F433);
		assertThat(despues.players()).hasSize(12);
		assertThat(despues.players().stream().filter(JugadorPlantillaDto::starter)).hasSize(11);
		assertThat(despues.players().stream().filter(j -> !j.starter())).singleElement()
				.satisfies(j -> assertThat(j.position()).isEqualTo(Position.CENTROCAMPISTA));
	}

	private List<Long> idDePosicion(PlantillaDto plantilla, Position posicion, int cuantos) {
		return plantilla.players().stream().filter(j -> j.position() == posicion).limit(cuantos).map(JugadorPlantillaDto::playerId).toList();
	}

	// --- Avance de jornada y puntuación ---

	@Test
	void avanzarLaJornadaPuntuaSoloAlosTitularesYOrdenaLaClasificacion() {
		LigaDto liga = ligaDeAnaConBeto();
		Match jornada1 = jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.FINALIZADO); // jornada 2 sin estadísticas

		// Un titular de Ana puntúa 9; un jugador del banquillo de Ana puntúa 50 pero no cuenta
		Long estrella = squadService.miPlantilla(ana.getId(), liga.id()).players().get(0).playerId();
		jdbc.update("update player_match_stats set points = 9.00 where player_id = ? and match_id = ?", estrella, jornada1.getId());
		Long suplente = libres(liga.id()).get(0);
		anadirABanquillo(liga.id(), miembroId(ana.getId(), liga.id()), suplente);
		jdbc.update("update player_match_stats set points = 50.00 where player_id = ? and match_id = ?", suplente, jornada1.getId());

		matchdayService.avanzar(liga.id(), false);

		List<ClasificacionDto> tabla = leagueService.clasificacion(ana.getId(), liga.id());
		assertThat(tabla).extracting(ClasificacionDto::username).containsExactly("ana", "beto");
		assertThat(tabla.get(0).totalPoints()).isEqualByComparingTo("19.00"); // 10 titulares x 1 + 9
		assertThat(tabla.get(0).lastMatchdayPoints()).isEqualByComparingTo("19.00");
		assertThat(tabla.get(0).me()).isTrue();
		assertThat(tabla.get(1).totalPoints()).isEqualByComparingTo("11.00");
		LigaDto despues = leagueService.detalle(ana.getId(), liga.id());
		assertThat(despues.currentMatchday()).isEqualTo(1);
		assertThat(despues.lastMatchday()).isEqualTo(2);
		assertThat(despues.nextAdvanceAt()).isAfter(Instant.now().plusSeconds(259200 - 60));
	}

	@Test
	void noSeAvanzaSiLaJornadaSiguienteNoTieneTodasSusEstadisticas() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.FINALIZADO);
		matchdayService.avanzar(liga.id(), false);

		esperaMotivo(HttpStatus.CONFLICT, "jornada 2", () -> matchdayService.avanzar(liga.id(), false));

		assertThat(leagueService.detalle(ana.getId(), liga.id()).currentMatchday()).isEqualTo(1);
		assertThat(leagueService.clasificacion(ana.getId(), liga.id())).allSatisfy(c -> assertThat(c.totalPoints()).isEqualByComparingTo("11.00"));
	}

	@Test
	void noSeAvanzaSiAlgunPartidoNoHaTerminado() {
		LigaDto liga = ligaDeAnaConBeto();
		partido(2024, 1, MatchStatus.PROGRAMADO);

		esperaMotivo(HttpStatus.CONFLICT, "jornada 1", () -> matchdayService.avanzar(liga.id(), false));
	}

	@Test
	void alTerminarLaUltimaJornadaLaLigaSeFinaliza() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		jornadaCompleta(2, "1.00");

		matchdayService.avanzar(liga.id(), false);
		matchdayService.avanzar(liga.id(), false);

		LigaDto fin = leagueService.detalle(ana.getId(), liga.id());
		assertThat(fin.status()).isEqualTo(LeagueStatus.FINALIZADA);
		assertThat(fin.currentMatchday()).isEqualTo(2);
		esperaMotivo(HttpStatus.CONFLICT, "terminado", () -> matchdayService.avanzar(liga.id(), false));
		JugadorMercadoDto jugador = primeroDelMercado(ana.getId(), liga.id());
		esperaMotivo(HttpStatus.CONFLICT, "terminado", () -> marketService.pujar(ana.getId(), liga.id(), jugador.playerId(), jugador.minBid()));
	}

	@Test
	void soloElDuenoPuedeGestionarElRitmoDeLaLiga() {
		LigaDto liga = ligaDeAnaConBeto();

		esperaEstado(HttpStatus.FORBIDDEN, () -> leagueService.exigirDueno(beto.getId(), liga.id()));
		esperaEstado(HttpStatus.FORBIDDEN, () -> leagueService.cambiarIntervalo(beto.getId(), liga.id(), 60));
		LigaDto cambiada = leagueService.cambiarIntervalo(ana.getId(), liga.id(), 30);
		assertThat(cambiada.intervalSeconds()).isEqualTo(30);
		assertThat(cambiada.nextAdvanceAt()).isBefore(Instant.now().plusSeconds(40));
	}

	// --- Reloj ---

	@Test
	void elRelojAvanzaLasLigasVencidasYDejaLasDemas() {
		LigaDto vencida = ligaDeAnaConBeto();
		LigaDto enPlazo = leagueService.crear(carla.getId(), "Liga en plazo");
		jornadaCompleta(1, "1.00");
		jdbc.update("update league set next_advance_at = now() - interval '1 minute' where id = ?", vencida.id());

		scheduler.avanzarLigasVencidas();

		assertThat(leagueService.detalle(ana.getId(), vencida.id()).currentMatchday()).isEqualTo(1);
		assertThat(leagueService.detalle(carla.getId(), enPlazo.id()).currentMatchday()).isZero();
	}

	/**
	 * Una jornada sin estadísticas ya no bloquea el reloj: se simula al pasar por ella. Lo que sí la bloquea
	 * es que no haya partidos en el calendario, y entonces el reloj tiene que aguantar el 409 sin romperse.
	 */
	@Test
	void elRelojSimulaLaJornadaQueLeFaltanEstadisticasYAvanza() {
		LigaDto liga = ligaDeAnaConBeto();
		partido(2024, 1, MatchStatus.FINALIZADO); // sin estadísticas: se simulan al avanzar
		jdbc.update("update league set next_advance_at = now() - interval '1 minute' where id = ?", liga.id());

		scheduler.avanzarLigasVencidas();

		assertThat(leagueService.detalle(ana.getId(), liga.id()).currentMatchday()).isEqualTo(1);
	}

	@Test
	void elRelojNoFallaNiAvanzaSiLaJornadaNoEstaEnElCalendario() {
		LigaDto liga = ligaDeAnaConBeto();
		jdbc.update("update league set next_advance_at = now() - interval '1 minute' where id = ?", liga.id());

		scheduler.avanzarLigasVencidas();

		assertThat(leagueService.detalle(ana.getId(), liga.id()).currentMatchday()).isZero();
	}

	// --- Pujas al mercado ---

	@Test
	void laPujaDebeEstarEntreElValorYElValorMasVeintePorCiento() {
		LigaDto liga = ligaDeAnaConBeto();
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());

		esperaMotivo(HttpStatus.BAD_REQUEST, "entre", () -> marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid().subtract(BigDecimal.ONE)));
		esperaMotivo(HttpStatus.BAD_REQUEST, "entre", () -> marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.maxBid().add(BigDecimal.ONE)));
		esperaMotivo(HttpStatus.BAD_REQUEST, "entero", () -> marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid().add(new BigDecimal("0.5"))));

		assertThat(marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid()).status()).isEqualTo(OfferStatus.PENDIENTE);
		assertThat(marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.maxBid()).amount()).isEqualByComparingTo(j.maxBid());
	}

	@Test
	void pujarDosVecesPorElMismoJugadorActualizaLaPuja() {
		LigaDto liga = ligaDeAnaConBeto();
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());

		marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid());
		marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.maxBid());

		assertThat(marketService.ofertas(ana.getId(), liga.id())).hasSize(1);
		assertThat(marketService.mercado(ana.getId(), liga.id()).stream().filter(m -> m.playerId().equals(j.playerId())).findFirst().orElseThrow().myBid())
				.isEqualByComparingTo(j.maxBid());
	}

	@Test
	void noSePuedePujarSinPresupuestoNiPorUnJugadorFueraDelMercado() {
		LigaDto liga = ligaDeAnaConBeto();
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());
		Long fueraDelMercado = libres(liga.id()).get(0);
		Player libre = playerRepository.findById(fueraDelMercado).orElseThrow();

		fijarPresupuesto(miembroId(ana.getId(), liga.id()), 1000);
		esperaMotivo(HttpStatus.CONFLICT, "Presupuesto", () -> marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid()));
		esperaMotivo(HttpStatus.BAD_REQUEST, "no está en el mercado", () -> marketService.pujar(beto.getId(), liga.id(), libre.getId(), libre.getMarketValue()));
	}

	@Test
	void conLaPlantillaCompletaNoSePuedePujar() {
		LigaDto liga = ligaDeAnaConBeto();
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());
		Long anaMiembro = miembroId(ana.getId(), liga.id());
		libres(liga.id()).stream().limit(7).forEach(id -> anadirABanquillo(liga.id(), anaMiembro, id));

		assertThat(squadService.miPlantilla(ana.getId(), liga.id()).players()).hasSize(18);
		esperaMotivo(HttpStatus.CONFLICT, "completa", () -> marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid()));
	}

	@Test
	void ganaLaPujaMasAltaYLaOtraSePierde() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.PROGRAMADO);
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());
		marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.maxBid());
		marketService.pujar(beto.getId(), liga.id(), j.playerId(), j.minBid());

		matchdayService.avanzar(liga.id(), false);

		PlantillaDto deAna = squadService.miPlantilla(ana.getId(), liga.id());
		assertThat(ids(deAna)).contains(j.playerId());
		assertThat(deAna.players().stream().filter(p -> p.playerId().equals(j.playerId())).findFirst().orElseThrow().starter()).isFalse();
		assertThat(deAna.budget()).isEqualByComparingTo(new BigDecimal("40000000").subtract(j.maxBid()));
		assertThat(ids(squadService.miPlantilla(beto.getId(), liga.id()))).doesNotContain(j.playerId());
		assertThat(squadService.miPlantilla(beto.getId(), liga.id()).budget()).isEqualByComparingTo("40000000");
		assertThat(oferta(ana.getId(), liga.id(), j.playerId()).status()).isEqualTo(OfferStatus.ACEPTADA);
		assertThat(oferta(beto.getId(), liga.id(), j.playerId()).status()).isEqualTo(OfferStatus.PERDIDA);
		// El mercado se renueva y ya no contiene al jugador adjudicado
		assertThat(marketService.mercado(ana.getId(), liga.id())).hasSize(10).extracting(JugadorMercadoDto::playerId).doesNotContain(j.playerId());
	}

	@Test
	void conIgualImporteGanaLaPrimeraPuja() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.PROGRAMADO);
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());
		marketService.pujar(beto.getId(), liga.id(), j.playerId(), j.minBid());
		marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid());

		matchdayService.avanzar(liga.id(), false);

		assertThat(ids(squadService.miPlantilla(beto.getId(), liga.id()))).contains(j.playerId());
		assertThat(oferta(ana.getId(), liga.id(), j.playerId()).status()).isEqualTo(OfferStatus.PERDIDA);
	}

	@Test
	void unaPujaSeAnulaSiAlResolverYaNoHayFondosYOptaLaSiguiente() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.PROGRAMADO);
		List<JugadorMercadoDto> mercado = marketService.mercado(ana.getId(), liga.id());
		JugadorMercadoDto caro = mercado.stream().max(Comparator.comparing(JugadorMercadoDto::value)).orElseThrow();
		JugadorMercadoDto barato = mercado.stream().min(Comparator.comparing(JugadorMercadoDto::value)).orElseThrow();
		assumeThat(caro.value()).isGreaterThan(barato.value());
		Long anaMiembro = miembroId(ana.getId(), liga.id());

		marketService.pujar(ana.getId(), liga.id(), caro.playerId(), caro.maxBid());
		marketService.pujar(ana.getId(), liga.id(), barato.playerId(), barato.minBid());
		marketService.pujar(beto.getId(), liga.id(), barato.playerId(), barato.minBid());
		fijarPresupuesto(anaMiembro, caro.maxBid().longValue()); // solo le da para el jugador caro

		matchdayService.avanzar(liga.id(), false);

		assertThat(oferta(ana.getId(), liga.id(), caro.playerId()).status()).isEqualTo(OfferStatus.ACEPTADA);
		assertThat(oferta(ana.getId(), liga.id(), barato.playerId()).status()).isEqualTo(OfferStatus.ANULADA);
		assertThat(ids(squadService.miPlantilla(beto.getId(), liga.id()))).contains(barato.playerId()); // opta la siguiente
		assertThat(presupuesto(anaMiembro)).isEqualByComparingTo("0");
	}

	@Test
	void unaPujaRetiradaNoSeResuelve() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.PROGRAMADO);
		JugadorMercadoDto j = primeroDelMercado(ana.getId(), liga.id());
		OfertaDto puja = marketService.pujar(ana.getId(), liga.id(), j.playerId(), j.minBid());

		assertThat(marketService.retirar(ana.getId(), liga.id(), puja.id()).status()).isEqualTo(OfferStatus.RETIRADA);
		esperaEstado(HttpStatus.NOT_FOUND, () -> marketService.retirar(beto.getId(), liga.id(), puja.id()));
		matchdayService.avanzar(liga.id(), false);

		assertThat(ids(squadService.miPlantilla(ana.getId(), liga.id()))).doesNotContain(j.playerId());
	}

	// --- Ofertas directas ---

	private Long jugadorDeAna(Long ligaId) {
		return squadService.miPlantilla(ana.getId(), ligaId).players().get(0).playerId();
	}

	@Test
	void laOfertaDirectaSigueLasMismasReglasDeImporte() {
		LigaDto liga = ligaDeAnaConBeto();
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		Long libre = libres(liga.id()).get(0);

		esperaMotivo(HttpStatus.BAD_REQUEST, "entre", () -> marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor.subtract(BigDecimal.ONE)));
		esperaMotivo(HttpStatus.BAD_REQUEST, "propio", () -> marketService.ofertarDirecta(ana.getId(), liga.id(), jugadorId, valor));
		esperaEstado(HttpStatus.NOT_FOUND, () -> marketService.ofertarDirecta(beto.getId(), liga.id(), libre, BigDecimal.TEN));
		assertThat(marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor).status()).isEqualTo(OfferStatus.PENDIENTE);
	}

	@Test
	void aceptarUnaOfertaDirectaTraspasaAlInstanteJugadorYDinero() {
		LigaDto liga = ligaDeAnaConBeto();
		leagueService.unirse(carla.getId(), liga.joinCode());
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		BigDecimal precio = MarketRules.maximo(valor, 20);
		OfertaDto deBeto = marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, precio);
		OfertaDto deCarla = marketService.ofertarDirecta(carla.getId(), liga.id(), jugadorId, valor);
		assertThat(marketService.ofertas(ana.getId(), liga.id())).filteredOn(OfertaDto::received).hasSize(2);

		OfertaDto aceptada = marketService.aceptar(ana.getId(), liga.id(), deBeto.id());

		assertThat(aceptada.status()).isEqualTo(OfferStatus.ACEPTADA);
		assertThat(ids(squadService.miPlantilla(ana.getId(), liga.id()))).doesNotContain(jugadorId).hasSize(10);
		PlantillaDto deBetoPlantilla = squadService.miPlantilla(beto.getId(), liga.id());
		assertThat(ids(deBetoPlantilla)).contains(jugadorId).hasSize(12);
		assertThat(deBetoPlantilla.players().stream().filter(p -> p.playerId().equals(jugadorId)).findFirst().orElseThrow().starter()).isFalse();
		assertThat(deBetoPlantilla.budget()).isEqualByComparingTo(new BigDecimal("40000000").subtract(precio));
		assertThat(squadService.miPlantilla(ana.getId(), liga.id()).budget()).isEqualByComparingTo(new BigDecimal("40000000").add(precio));
		assertThat(oferta(carla.getId(), liga.id(), jugadorId).status()).isEqualTo(OfferStatus.ANULADA);
		assertThat(deCarla.id()).isNotEqualTo(deBeto.id());
	}

	@Test
	void unaOfertaDirectaSePuedeRechazarYSoloLaResuelveSuDueno() {
		LigaDto liga = ligaDeAnaConBeto();
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		OfertaDto oferta = marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor);

		esperaEstado(HttpStatus.NOT_FOUND, () -> marketService.aceptar(beto.getId(), liga.id(), oferta.id()));
		assertThat(marketService.rechazar(ana.getId(), liga.id(), oferta.id()).status()).isEqualTo(OfferStatus.RECHAZADA);
		esperaMotivo(HttpStatus.CONFLICT, "pendiente", () -> marketService.aceptar(ana.getId(), liga.id(), oferta.id()));
		assertThat(ids(squadService.miPlantilla(ana.getId(), liga.id()))).contains(jugadorId);
	}

	@Test
	void sielCompradorYaNoTieneFondosAlAceptarLaOfertaSeAnula() {
		LigaDto liga = ligaDeAnaConBeto();
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		OfertaDto oferta = marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor);
		fijarPresupuesto(miembroId(beto.getId(), liga.id()), 0);

		esperaMotivo(HttpStatus.CONFLICT, "presupuesto", () -> marketService.aceptar(ana.getId(), liga.id(), oferta.id()));

		assertThat(oferta(beto.getId(), liga.id(), jugadorId).status()).isEqualTo(OfferStatus.ANULADA);
		assertThat(ids(squadService.miPlantilla(ana.getId(), liga.id()))).contains(jugadorId);
	}

	@Test
	void lasOfertasDirectasSinResponderCaducanAlAvanzarLaJornada() {
		LigaDto liga = ligaDeAnaConBeto();
		jornadaCompleta(1, "1.00");
		partido(2024, 2, MatchStatus.PROGRAMADO);
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		OfertaDto oferta = marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor);

		matchdayService.avanzar(liga.id(), false);

		assertThat(oferta(beto.getId(), liga.id(), jugadorId).status()).isEqualTo(OfferStatus.CADUCADA);
		esperaMotivo(HttpStatus.CONFLICT, "pendiente", () -> marketService.aceptar(ana.getId(), liga.id(), oferta.id()));
	}

	@Test
	void dosAceptacionesSimultaneasSoloTraspasanUnaVez() throws Exception {
		LigaDto liga = ligaDeAnaConBeto();
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		OfertaDto oferta = marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor);

		ExecutorService hilos = Executors.newFixedThreadPool(2);
		CountDownLatch salida = new CountDownLatch(1);
		Callable<Boolean> aceptar = () -> {
			salida.await();
			try {
				marketService.aceptar(ana.getId(), liga.id(), oferta.id());
				return true;
			} catch (ResponseStatusException e) {
				return false;
			}
		};
		Future<Boolean> primero = hilos.submit(aceptar);
		Future<Boolean> segundo = hilos.submit(aceptar);
		salida.countDown();
		boolean a = primero.get();
		boolean b = segundo.get();
		hilos.shutdown();

		assertThat(a ^ b).as("exactamente una aceptación debe prosperar").isTrue();
		assertThat(presupuesto(miembroId(beto.getId(), liga.id()))).isEqualByComparingTo(new BigDecimal("40000000").subtract(valor));
		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo(new BigDecimal("40000000").add(valor));
	}

	// --- Vender ---

	@Test
	void venderUnJugadorDevuelveSuValorLiberaHuecoYAnulaOfertas() {
		LigaDto liga = ligaDeAnaConBeto();
		Long jugadorId = jugadorDeAna(liga.id());
		BigDecimal valor = playerRepository.findById(jugadorId).orElseThrow().getMarketValue();
		marketService.ofertarDirecta(beto.getId(), liga.id(), jugadorId, valor);

		PlantillaDto despues = squadService.vender(ana.getId(), liga.id(), jugadorId);

		assertThat(despues.players()).hasSize(10);
		assertThat(ids(despues)).doesNotContain(jugadorId);
		assertThat(despues.budget()).isEqualByComparingTo(new BigDecimal("40000000").add(valor));
		assertThat(oferta(beto.getId(), liga.id(), jugadorId).status()).isEqualTo(OfferStatus.ANULADA);
		assertThat(libres(liga.id())).contains(jugadorId);
		esperaEstado(HttpStatus.NOT_FOUND, () -> squadService.vender(ana.getId(), liga.id(), jugadorId));
	}

	@Test
	void noSePuedeVenderUnJugadorAjeno() {
		LigaDto liga = ligaDeAnaConBeto();
		Long deBeto = squadService.miPlantilla(beto.getId(), liga.id()).players().get(0).playerId();

		esperaEstado(HttpStatus.NOT_FOUND, () -> squadService.vender(ana.getId(), liga.id(), deBeto));
	}

	@Test
	void sePuedeVerLaPlantillaDeUnRivalPeroSoloDentroDeLaLiga() {
		LigaDto liga = ligaDeAnaConBeto();
		Long betoMiembro = miembroId(beto.getId(), liga.id());
		LigaDto otra = leagueService.crear(carla.getId(), "Otra liga");

		assertThat(squadService.plantillaDe(ana.getId(), liga.id(), betoMiembro).username()).isEqualTo("beto");
		esperaEstado(HttpStatus.NOT_FOUND, () -> squadService.plantillaDe(carla.getId(), liga.id(), betoMiembro));
		esperaEstado(HttpStatus.NOT_FOUND, () -> squadService.plantillaDe(carla.getId(), otra.id(), betoMiembro));
	}

	// --- Opciones de la liga: abonos y expulsiones ---

	@Test
	void porDefectoSeAbonan100000PorPuntoYPremiosPorPuesto() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		assertThat(liga.pointValue()).isEqualByComparingTo("100000");
		assertThat(liga.positionBonuses()).extracting(BigDecimal::longValue).containsExactly(3_000_000L, 2_000_000L, 1_000_000L);
		jornadaDondeGanaAna(liga);

		matchdayService.avanzar(liga.id(), false);

		// Ana: 19 puntos x 100.000 + 3.000.000 por ser 1.ª. Beto: 11 x 100.000 + 2.000.000 por ser 2.º.
		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo("44900000");
		assertThat(presupuesto(miembroId(beto.getId(), liga.id()))).isEqualByComparingTo("43100000");
	}

	@Test
	void elDuenoCambiaElValorDelPuntoYLosPremiosYRigeEnLaSiguienteJornada() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		LigaDto cambiada = leagueService.cambiarOpciones(ana.getId(), liga.id(), AdvanceMode.POR_TIEMPO, new BigDecimal("200000"), List.of(new BigDecimal("500000")));
		assertThat(cambiada.pointValue()).isEqualByComparingTo("200000");
		assertThat(cambiada.positionBonuses()).hasSize(1);
		assertThat(leagueService.detalle(beto.getId(), liga.id()).pointValue()).isEqualByComparingTo("200000");
		jornadaDondeGanaAna(liga);

		matchdayService.avanzar(liga.id(), false);

		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo("44300000"); // 19 x 200.000 + 500.000
		assertThat(presupuesto(miembroId(beto.getId(), liga.id()))).isEqualByComparingTo("42200000"); // 11 x 200.000, sin premio
	}

	@Test
	void soloElDuenoPuedeCambiarLasOpciones() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();

		esperaEstado(HttpStatus.FORBIDDEN, () -> leagueService.cambiarOpciones(beto.getId(), liga.id(), AdvanceMode.POR_TIEMPO, BigDecimal.ONE, List.of()));
		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.cambiarOpciones(carla.getId(), liga.id(), AdvanceMode.POR_TIEMPO, BigDecimal.ONE, List.of()));
		assertThat(leagueService.detalle(ana.getId(), liga.id()).pointValue()).isEqualByComparingTo("100000");
	}

	@Test
	void losEmpatesComparteuPuestoYPremio() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		jornadaCompleta(1, "1.00"); // los dos suman 11

		matchdayService.avanzar(liga.id(), false);

		// Los dos son 1.º: 11 x 100.000 + 3.000.000 cada uno
		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo("44100000");
		assertThat(presupuesto(miembroId(beto.getId(), liga.id()))).isEqualByComparingTo("44100000");
	}

	@Test
	void quienNoSumaPuntosPositivosNoCobraNada() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		jornadaCompleta(1, "0.00");

		matchdayService.avanzar(liga.id(), false);

		assertThat(presupuesto(miembroId(ana.getId(), liga.id()))).isEqualByComparingTo("40000000");
		assertThat(presupuesto(miembroId(beto.getId(), liga.id()))).isEqualByComparingTo("40000000");
	}

	@Test
	void laClasificacionPorAbonosOrdenaPorLoCobradoYDesglosaElOrigen() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();
		jornadaDondeGanaAna(liga);
		matchdayService.avanzar(liga.id(), false);

		List<AbonoDto> abonos = leagueService.abonos(beto.getId(), liga.id());

		assertThat(abonos).extracting(AbonoDto::username).containsExactly("ana", "beto");
		assertThat(abonos.get(0).position()).isEqualTo(1);
		assertThat(abonos.get(0).totalPayout()).isEqualByComparingTo("4900000");
		assertThat(abonos.get(0).totalFromPoints()).isEqualByComparingTo("1900000");
		assertThat(abonos.get(0).totalFromPosition()).isEqualByComparingTo("3000000");
		assertThat(abonos.get(0).lastMatchdayPosition()).isEqualTo(1);
		assertThat(abonos.get(1).me()).isTrue();
		assertThat(abonos.get(1).lastMatchdayPosition()).isEqualTo(2);
		assertThat(abonos.get(1).lastMatchdayPayout()).isEqualByComparingTo("3100000");
	}

	@Test
	void sinJornadasPuntuadasLosAbonosEstanAZero() {
		LigaDto liga = ligaConAbonosDeAnaConBeto();

		List<AbonoDto> abonos = leagueService.abonos(ana.getId(), liga.id());

		assertThat(abonos).hasSize(2).allSatisfy(a -> {
			assertThat(a.totalPayout()).isEqualByComparingTo("0");
			assertThat(a.lastMatchdayPosition()).isNull();
		});
	}

	@Test
	void elDuenoPuedeExpulsarAUnMiembroYSusJugadoresQuedanLibres() {
		LigaDto liga = ligaDeAnaConBeto();
		Long betoMiembro = miembroId(beto.getId(), liga.id());
		PlantillaDto deBeto = squadService.miPlantilla(beto.getId(), liga.id());
		Set<Long> jugadoresDeBeto = ids(deBeto);
		JugadorPlantillaDto objetivo = deBeto.players().get(0);
		marketService.ofertarDirecta(ana.getId(), liga.id(), objetivo.playerId(), objetivo.value());
		Long deAna = squadService.miPlantilla(ana.getId(), liga.id()).players().get(0).playerId();

		leagueService.expulsar(ana.getId(), liga.id(), betoMiembro);

		assertThat(leagueService.detalle(ana.getId(), liga.id()).members()).isEqualTo(1);
		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.detalle(beto.getId(), liga.id()));
		assertThat(libres(liga.id())).containsAll(jugadoresDeBeto);
		assertThat(jdbc.queryForObject("select count(*) from offer where league_id = ?", Integer.class, liga.id())).isZero();
		assertThat(ids(squadService.miPlantilla(ana.getId(), liga.id()))).contains(deAna).hasSize(11);
		// Otro usuario puede entrar a ocupar el hueco mientras la liga no haya empezado
		assertThat(leagueService.unirse(carla.getId(), liga.joinCode()).members()).isEqualTo(2);
	}

	@Test
	void soloElDuenoPuedeExpulsarYNoPuedeExpulsarseASiMismo() {
		LigaDto liga = ligaDeAnaConBeto();
		leagueService.unirse(carla.getId(), liga.joinCode());
		Long anaMiembro = miembroId(ana.getId(), liga.id());
		Long carlaMiembro = miembroId(carla.getId(), liga.id());

		esperaEstado(HttpStatus.FORBIDDEN, () -> leagueService.expulsar(beto.getId(), liga.id(), carlaMiembro));
		esperaMotivo(HttpStatus.CONFLICT, "propia liga", () -> leagueService.expulsar(ana.getId(), liga.id(), anaMiembro));
		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.expulsar(ana.getId(), liga.id(), 999_999L));
		assertThat(leagueService.detalle(ana.getId(), liga.id()).members()).isEqualTo(3);
	}

	@Test
	void noSePuedeExpulsarAUnMiembroDeOtraLiga() {
		LigaDto liga = ligaDeAnaConBeto();
		LigaDto otra = leagueService.crear(carla.getId(), "Otra liga");
		Long carlaMiembro = miembroId(carla.getId(), otra.id());

		esperaEstado(HttpStatus.NOT_FOUND, () -> leagueService.expulsar(ana.getId(), liga.id(), carlaMiembro));
		assertThat(leagueService.detalle(carla.getId(), otra.id()).members()).isEqualTo(1);
	}
}
