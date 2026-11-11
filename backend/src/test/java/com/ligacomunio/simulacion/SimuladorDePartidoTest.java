package com.ligacomunio.simulacion;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import com.ligacomunio.simulacion.SimuladorDePartido.Actuacion;
import com.ligacomunio.simulacion.SimuladorDePartido.PartidoSimulado;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El motor de simulación es lógica pura, así que se prueba sin Spring y sin base de datos. */
class SimuladorDePartidoTest {

	private static final long SEMILLA = 2024L;
	private static final long PARTIDO = 501L;

	private final SimuladorDePartido simulador = new SimuladorDePartido();

	private long siguienteId = 1;

	/** Plantilla con de sobra para un 4-4-2 y siete suplentes, con valores distintos para que haya niveles. */
	private List<Player> plantilla(long valorBase) {
		List<Player> jugadores = new ArrayList<>();
		anadir(jugadores, Position.PORTERO, 3, valorBase);
		anadir(jugadores, Position.DEFENSA, 7, valorBase);
		anadir(jugadores, Position.CENTROCAMPISTA, 7, valorBase);
		anadir(jugadores, Position.DELANTERO, 5, valorBase);
		return jugadores;
	}

	private void anadir(List<Player> destino, Position posicion, int cuantos, long valorBase) {
		for (int i = 0; i < cuantos; i++) {
			Player p = new Player();
			ReflectionTestUtils.setField(p, "id", siguienteId++);
			p.setName(posicion.name().charAt(0) + "" + siguienteId);
			p.setPosition(posicion);
			p.setMarketValue(BigDecimal.valueOf(valorBase + i * 800_000L));
			destino.add(p);
		}
	}

	private long golesDe(PartidoSimulado partido, List<Player> equipo) {
		return partido.actuaciones().stream()
				.filter(a -> equipo.contains(a.jugador()))
				.mapToInt(Actuacion::goles)
				.sum();
	}

	private List<Actuacion> deEquipo(PartidoSimulado partido, List<Player> equipo) {
		return partido.actuaciones().stream().filter(a -> equipo.contains(a.jugador())).toList();
	}

	@Test
	void respetaElMarcadorRealYRepartaEsosGolesEntreLosJugadores() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 3, 1);

		assertThat(partido.golesLocal()).isEqualTo(3);
		assertThat(partido.golesVisitante()).isEqualTo(1);
		assertThat(golesDe(partido, local)).isEqualTo(3);
		assertThat(golesDe(partido, visitante)).isEqualTo(1);
	}

	@Test
	void sinMarcadorRealSeLoInventa() {
		List<Player> local = plantilla(5_000_000L);
		List<Player> visitante = plantilla(5_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, null, null);

		assertThat(partido.golesLocal()).isNotNegative();
		assertThat(partido.golesVisitante()).isNotNegative();
		assertThat(golesDe(partido, local)).isEqualTo(partido.golesLocal());
		assertThat(golesDe(partido, visitante)).isEqualTo(partido.golesVisitante());
	}

	@Test
	void mismaSemillaMismoPartido() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado primera = simulador.simular(SEMILLA, PARTIDO, local, visitante, 2, 2);
		PartidoSimulado segunda = simulador.simular(SEMILLA, PARTIDO, local, visitante, 2, 2);

		assertThat(resumen(segunda)).isEqualTo(resumen(primera));
	}

	@Test
	void otraSemillaOtroPartido() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado conUna = simulador.simular(SEMILLA, PARTIDO, local, visitante, 2, 2);
		PartidoSimulado conOtra = simulador.simular(SEMILLA + 1, PARTIDO, local, visitante, 2, 2);

		assertThat(resumen(conOtra)).isNotEqualTo(resumen(conUna));
	}

	private List<String> resumen(PartidoSimulado partido) {
		return partido.actuaciones().stream()
				.map(a -> a.jugador().getId() + ":" + a.minutos() + ":" + a.goles() + ":" + a.asistencias()
						+ ":" + a.entradas() + ":" + a.pasesCompletados() + ":" + a.paradas())
				.sorted()
				.toList();
	}

	@Test
	void juegan11TitularesPorEquipoMasLosCambiosYNadieConCeroMinutos() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 1, 0);

		for (List<Player> equipo : List.of(local, visitante)) {
			List<Actuacion> actuaciones = deEquipo(partido, equipo);
			// 11 titulares + entre 2 y 4 cambios
			assertThat(actuaciones).hasSizeBetween(13, 15);
			assertThat(actuaciones).allSatisfy(a -> assertThat(a.minutos()).isPositive());
			assertThat(actuaciones).extracting(Actuacion::jugador).doesNotHaveDuplicates();
			// Exactamente un portero y nunca se cambia
			List<Actuacion> porteros = actuaciones.stream()
					.filter(a -> a.jugador().getPosition() == Position.PORTERO).toList();
			assertThat(porteros).hasSize(1);
			assertThat(porteros.get(0).minutos()).isEqualTo(90);
		}
	}

	@Test
	void elPorteroEncajaLosGolesDelRivalYSusTirosRecibidosCuadran() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 1, 4);

		Actuacion porteroLocal = porteroDe(partido, local);
		Actuacion porteroVisitante = porteroDe(partido, visitante);
		assertThat(porteroLocal.golesEncajados()).isEqualTo(4);
		assertThat(porteroVisitante.golesEncajados()).isEqualTo(1);
		assertThat(porteroLocal.tirosRecibidos()).isEqualTo(porteroLocal.paradas() + porteroLocal.golesEncajados());
		assertThat(porteroVisitante.tirosRecibidos())
				.isEqualTo(porteroVisitante.paradas() + porteroVisitante.golesEncajados());
	}

	private Actuacion porteroDe(PartidoSimulado partido, List<Player> equipo) {
		return deEquipo(partido, equipo).stream()
				.filter(a -> a.jugador().getPosition() == Position.PORTERO)
				.findFirst().orElseThrow();
	}

	@Test
	void losDatosDeCadaJugadorSonCoherentes() {
		List<Player> local = plantilla(4_000_000L);
		List<Player> visitante = plantilla(9_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 2, 1);

		assertThat(partido.actuaciones()).allSatisfy(a -> {
			assertThat(a.minutos()).isBetween(1, 90);
			assertThat(a.goles()).isNotNegative();
			assertThat(a.asistencias()).isNotNegative();
			assertThat(a.pasesCompletados()).isNotNegative();
			assertThat(a.pasesFallados()).isNotNegative();
			assertThat(a.amarillas()).isBetween(0, 1);
			assertThat(a.rojas()).isBetween(0, 1);
			assertThat(a.rating()).isBetween(new BigDecimal("4.0"), new BigDecimal("9.5"));
			// Los campos de portero solo se rellenan para porteros, igual que en la importación real.
			if (a.jugador().getPosition() != Position.PORTERO) {
				assertThat(a.paradas()).isZero();
				assertThat(a.golesEncajados()).isZero();
				assertThat(a.tirosRecibidos()).isZero();
			}
		});
	}

	/** Las asistencias nunca pasan de los goles: una por gol como mucho. */
	@Test
	void lasAsistenciasNoSuperanALosGoles() {
		List<Player> local = plantilla(6_000_000L);
		List<Player> visitante = plantilla(6_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 4, 3);

		long asistenciasLocal = deEquipo(partido, local).stream().mapToInt(Actuacion::asistencias).sum();
		long asistenciasVisitante = deEquipo(partido, visitante).stream().mapToInt(Actuacion::asistencias).sum();
		assertThat(asistenciasLocal).isLessThanOrEqualTo(4);
		assertThat(asistenciasVisitante).isLessThanOrEqualTo(3);
	}

	/** Con plantillas incompletas no se cuelga ni se inventa gente: alinea a quien haya. */
	@Test
	void conUnEquipoCortoAlineaALosQueHaya() {
		List<Player> local = new ArrayList<>();
		anadir(local, Position.PORTERO, 1, 1_000_000L);
		anadir(local, Position.DEFENSA, 3, 1_000_000L);
		anadir(local, Position.CENTROCAMPISTA, 2, 1_000_000L);
		List<Player> visitante = plantilla(5_000_000L);

		PartidoSimulado partido = simulador.simular(SEMILLA, PARTIDO, local, visitante, 0, 2);

		assertThat(deEquipo(partido, local)).hasSize(6);
		assertThat(deEquipo(partido, local)).allSatisfy(a -> assertThat(a.minutos()).isEqualTo(90));
	}
}
