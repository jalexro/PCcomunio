package com.ligacomunio.valores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.player.PlayerStatus;
import com.ligacomunio.player.Position;
import com.ligacomunio.team.Team;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValueImportServiceTest {

	private final PlayerRepository repositorio = mock(PlayerRepository.class);
	private final ExternalValueRepository guardados = mock(ExternalValueRepository.class);
	private final ValueImportService servicio = new ValueImportService(repositorio, guardados);
	private long siguienteId = 1;

	private Team equipo(String nombre) {
		Team t = new Team();
		t.setName(nombre);
		return t;
	}

	private Player jugador(String nombre, Position posicion, Team equipo) {
		Player p = new Player();
		p.setId(siguienteId++);
		p.setName(nombre);
		p.setPosition(posicion);
		p.setTeam(equipo);
		return p;
	}

	private static ValorExterno valor(String nombre, String slug, String equipo, Position posicion, long valor) {
		return new ValorExterno(nombre, slug, equipo, posicion, valor, PlayerStatus.DISPONIBLE);
	}

	@Test
	void emparejaPorNombreCortoDentroDelEquipoAunqueLosNombresDeEquipoDifieran() {
		Team barca = equipo("Barcelona");
		Team athletic = equipo("Athletic Club");
		Player balde = jugador("Alejandro Balde", Position.DEFENSA, barca);
		Player williams = jugador("Iñaki Williams", Position.DELANTERO, athletic);
		when(repositorio.buscar(null, null)).thenReturn(List.of(balde, williams));

		ValoresResultado r = servicio.importar(List.of(
				valor("Balde", "alejandro-balde", "Barcelona", Position.DEFENSA, 1_130_000),
				valor("Williams", "inaki-williams", "Athletic", Position.DELANTERO, 5_000_000)));

		assertThat(r.emparejados()).isEqualTo(2);
		assertThat(balde.getMarketValue()).isEqualByComparingTo(new BigDecimal(1_130_000));
		assertThat(williams.getMarketValue()).isEqualByComparingTo(new BigDecimal(5_000_000));
		assertThat(r.sinEmparejar()).isEmpty();
		assertThat(r.jugadoresSinValor()).isEmpty();
	}

	@Test
	void ignoraTildesYMayusculas() {
		Player p = jugador("Fermín López", Position.CENTROCAMPISTA, equipo("Barcelona"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		servicio.importar(List.of(valor("FERMIN LOPEZ", "fermin-lopez", "Barcelona", Position.CENTROCAMPISTA, 9_000_000)));

		assertThat(p.getMarketValue()).isEqualByComparingTo(new BigDecimal(9_000_000));
	}

	@Test
	void unNombreAmbiguoSeDesempataPorPosicionYSiSigueAmbiguoNoSeAsigna() {
		Team t = equipo("Getafe");
		Player portero = jugador("David Soria", Position.PORTERO, t);
		Player delantero = jugador("David Silva", Position.DELANTERO, t);
		Player otroDelantero = jugador("David Mayoral", Position.DELANTERO, t);
		when(repositorio.buscar(null, null)).thenReturn(List.of(portero, delantero, otroDelantero));

		ValoresResultado r = servicio.importar(List.of(
				valor("David", "david", "Getafe", Position.PORTERO, 3_000_000),
				valor("David", "david", "Getafe", Position.DELANTERO, 4_000_000)));

		assertThat(portero.getMarketValue()).isEqualByComparingTo(new BigDecimal(3_000_000));
		assertThat(delantero.getMarketValue().signum()).isZero();
		assertThat(otroDelantero.getMarketValue().signum()).isZero();
		assertThat(r.sinEmparejar()).containsExactly("David (Getafe)");
	}

	@Test
	void unFichajeSeEncuentraPorNombreCompletoEnOtroEquipo() {
		Player p = jugador("Dani Olmo", Position.CENTROCAMPISTA, equipo("Leganes"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		ValoresResultado r = servicio.importar(List.of(valor("Dani Olmo", "dani-olmo", "Barcelona", Position.CENTROCAMPISTA, 8_000_000)));

		assertThat(r.emparejados()).isEqualTo(1);
		assertThat(r.emparejadosEnOtroEquipo()).isEqualTo(1);
		assertThat(r.equiposSinCorrespondencia()).containsExactly("Barcelona");
	}

	@Test
	void unNombreCortoNoSeBuscaEnOtrosEquipos() {
		Player p = jugador("Carlos Martin", Position.DELANTERO, equipo("Mallorca"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		ValoresResultado r = servicio.importar(List.of(valor("Martin", "martin", "Betis", Position.DELANTERO, 2_000_000)));

		assertThat(r.emparejados()).isZero();
		assertThat(r.jugadoresSinValor()).containsExactly("Carlos Martin (Mallorca)");
	}

	@Test
	void unJugadorDeLaBaseDeDatosNoRecibeDosValores() {
		Team t = equipo("Sevilla");
		Player p = jugador("Jose Angel Carmona", Position.DEFENSA, t);
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		ValoresResultado r = servicio.importar(List.of(
				valor("Carmona", "carmona", "Sevilla", Position.DEFENSA, 2_000_000),
				valor("Carmona", "carmona", "Sevilla", Position.DEFENSA, 9_000_000)));

		assertThat(p.getMarketValue()).isEqualByComparingTo(new BigDecimal(2_000_000));
		assertThat(r.emparejados()).isEqualTo(1);
		assertThat(r.sinEmparejar()).hasSize(1);
	}

	@Test
	void unFichajeConNombreAbreviadoSeEncuentraEnOtroEquipoSiEsUnicoYDeLaMismaPosicion() {
		Player gorosabel = jugador("Andoni Gorosabel", Position.DEFENSA, equipo("Athletic Club"));
		Player abqar = jugador("Abdelkabir Abqar", Position.DEFENSA, equipo("Alaves"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(gorosabel, abqar));

		ValoresResultado r = servicio.importar(List.of(
				valor("Gorosabel", "a-gorosabel", "Espanyol", Position.DEFENSA, 1_500_000),
				valor("Abqar", "abdel-abqar", "Getafe", Position.DEFENSA, 2_000_000)));

		assertThat(gorosabel.getMarketValue()).isEqualByComparingTo(new BigDecimal(1_500_000));
		assertThat(abqar.getMarketValue()).isEqualByComparingTo(new BigDecimal(2_000_000));
		assertThat(r.emparejadosEnOtroEquipo()).isEqualTo(2);
	}

	@Test
	void elNombreAbreviadoNoSeAsignaSiLaPosicionNoCoincide() {
		Player p = jugador("Andoni Gorosabel", Position.DEFENSA, equipo("Athletic Club"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		servicio.importar(List.of(valor("Gorosabel", "a-gorosabel", "Espanyol", Position.DELANTERO, 1_500_000)));

		assertThat(p.getMarketValue().signum()).isZero();
	}

	@Test
	void unJugadorDelEquipoTienePrioridadSobreUnFichajeDeOtroEquipo() {
		Player real = jugador("Daniel Carvajal", Position.DEFENSA, equipo("Real Madrid"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(real));

		servicio.importar(List.of(
				valor("Carvajal", "carvajal", "Getafe", Position.DEFENSA, 500_000),
				valor("Carvajal", "dani-carvajal", "Real Madrid", Position.DEFENSA, 7_000_000)));

		assertThat(real.getMarketValue()).isEqualByComparingTo(new BigDecimal(7_000_000));
	}

	@Test
	void guardaElEstadoDelJugador() {
		Player p = jugador("Alejandro Balde", Position.DEFENSA, equipo("Barcelona"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));

		servicio.importar(List.of(new ValorExterno("Balde", "alejandro-balde", "Barcelona", Position.DEFENSA, 1_000_000,
				PlayerStatus.SANCIONADO)));

		assertThat(p.getStatus()).isEqualTo(PlayerStatus.SANCIONADO);
	}

	@Test
	void reaplicarUsaLosValoresGuardadosYNoHaceNadaSiNoHay() {
		Player p = jugador("Alejandro Balde", Position.DEFENSA, equipo("Barcelona"));
		when(repositorio.buscar(null, null)).thenReturn(List.of(p));
		assertThat(servicio.reaplicar()).isNull();

		ExternalValue guardado = ExternalValue.de(valor("Balde", "alejandro-balde", "Barcelona", Position.DEFENSA, 1_130_000));
		when(guardados.findAll()).thenReturn(List.of(guardado));
		ValoresResultado r = servicio.reaplicar();

		assertThat(r.emparejados()).isEqualTo(1);
		assertThat(p.getMarketValue()).isEqualByComparingTo(new BigDecimal(1_130_000));
	}

	@Test
	void elLectorUsaPriceDescartaEntrenadoresYTraduceEquiposYPosiciones() {
		String json = """
				{"status":200,"data":{
				  "teams":{"3":{"id":3,"name":"Barcelona"}},
				  "players":{
				    "10040":{"id":10040,"name":"Balde","slug":"alejandro-balde","teamID":3,"position":2,"price":1130000,"fantasyPrice":44000000,"status":"injured"},
				    "99":{"id":99,"name":"Flick","slug":"flick","teamID":3,"position":5,"price":500000,"fantasyPrice":1000000},
				    "98":{"id":98,"name":"Sin precio","slug":"sin-precio","teamID":3,"position":4,"price":0}
				  }}}
				""";
		List<ValorExterno> valores = new BiwengerParser(new ObjectMapper())
				.leer(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

		assertThat(valores).containsExactly(new ValorExterno("Balde", "alejandro-balde", "Barcelona", Position.DEFENSA, 1_130_000, PlayerStatus.LESIONADO));
	}

	@Test
	void elLectorRechazaUnArchivoQueNoEsDeBiwenger() {
		BiwengerParser parser = new BiwengerParser(new ObjectMapper());

		assertThatThrownBy(() -> parser.leer(new ByteArrayInputStream("{\"otra\":1}".getBytes(StandardCharsets.UTF_8))))
				.isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> parser.leer(new ByteArrayInputStream("no es json".getBytes(StandardCharsets.UTF_8))))
				.isInstanceOf(ResponseStatusException.class);
	}
}
