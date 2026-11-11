package com.ligacomunio.league;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sorteo de la plantilla inicial: lógica pura, sin base de datos ni contexto de Spring. */
class SorteoDePlantillaTest {

	private static final Map<Position, Integer> CUPOS = new LinkedHashMap<>();

	static {
		CUPOS.put(Position.PORTERO, 1);
		CUPOS.put(Position.DEFENSA, 4);
		CUPOS.put(Position.CENTROCAMPISTA, 4);
		CUPOS.put(Position.DELANTERO, 2);
	}

	/** La más barata posible con este catálogo: 1 + (1+2+3+4) + (1+2+3+4) + (1+2) = 24 millones. */
	private static final BigDecimal MAS_BARATA = millones(24);

	private long siguienteId = 1;

	private static BigDecimal millones(long cuantos) {
		return BigDecimal.valueOf(cuantos * 1_000_000L);
	}

	/** Doce jugadores por posición, de 1 a 12 millones. */
	private Map<Position, List<Player>> catalogo() {
		return catalogo(12);
	}

	private Map<Position, List<Player>> catalogo(int porPosicion) {
		Map<Position, List<Player>> candidatos = new EnumMap<>(Position.class);
		for (Position posicion : Position.values()) {
			List<Player> jugadores = new ArrayList<>();
			for (int i = 1; i <= porPosicion; i++) {
				Player p = new Player();
				p.setId(siguienteId++);
				p.setName(posicion + " " + i);
				p.setPosition(posicion);
				p.setMarketValue(millones(i));
				jugadores.add(p);
			}
			candidatos.put(posicion, jugadores);
		}
		return candidatos;
	}

	private static BigDecimal suma(List<Player> elegidos) {
		return elegidos.stream().map(Player::getMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static void esPlantillaValida(List<Player> elegidos) {
		assertThat(elegidos).hasSize(Formation.TITULARES);
		assertThat(elegidos).extracting(Player::getId).doesNotHaveDuplicates();
		Map<Position, Long> composicion = elegidos.stream()
				.collect(Collectors.groupingBy(Player::getPosition, Collectors.counting()));
		assertThat(composicion).containsEntry(Position.PORTERO, 1L).containsEntry(Position.DEFENSA, 4L)
				.containsEntry(Position.CENTROCAMPISTA, 4L).containsEntry(Position.DELANTERO, 2L);
	}

	@Test
	void elRepartoAleatorioDaLosCuposPedidos() {
		esPlantillaValida(SorteoDePlantilla.aleatorio(CUPOS, catalogo(), new Random(1)));
	}

	@Test
	void elRepartoConTopeNuncaSePasaDelTope() {
		BigDecimal tope = millones(40);
		for (int semilla = 0; semilla < 300; semilla++) {
			List<Player> elegidos = SorteoDePlantilla.conTope(CUPOS, catalogo(), tope, new Random(semilla));
			esPlantillaValida(elegidos);
			assertThat(suma(elegidos)).as("semilla %d", semilla).isLessThanOrEqualTo(tope);
		}
	}

	@Test
	void elRepartoConTopeAprovechaElTopeYNoRepiteSiempreLaMismaPlantilla() {
		BigDecimal tope = millones(40);
		Set<Set<Long>> distintas = new HashSet<>();
		BigDecimal total = BigDecimal.ZERO;
		int vueltas = 300;
		for (int semilla = 0; semilla < vueltas; semilla++) {
			List<Player> elegidos = SorteoDePlantilla.conTope(CUPOS, catalogo(), tope, new Random(semilla));
			distintas.add(elegidos.stream().map(Player::getId).collect(Collectors.toSet()));
			total = total.add(suma(elegidos));
		}
		// Aleatoriedad real: no es "siempre los 11 más baratos", que daría una sola plantilla.
		assertThat(distintas).hasSizeGreaterThan(vueltas / 2);
		// Y se usa buena parte del tope: la media queda por encima del punto medio entre lo más barato posible
		// (24 M con este catálogo) y el tope.
		BigDecimal media = total.divide(BigDecimal.valueOf(vueltas), 2, java.math.RoundingMode.HALF_UP);
		assertThat(media).isGreaterThan(MAS_BARATA.add(tope.subtract(MAS_BARATA).divide(BigDecimal.TWO)));
	}

	/** Con un tope ajustadísimo (24 M es el mínimo posible) solo cabe la plantilla más barata, y sale. */
	@Test
	void conElTopeJustoSaleLaPlantillaMasBarata() {
		List<Player> elegidos = SorteoDePlantilla.conTope(CUPOS, catalogo(), MAS_BARATA, new Random(7));

		esPlantillaValida(elegidos);
		assertThat(suma(elegidos)).isEqualByComparingTo(MAS_BARATA);
	}

	@Test
	void unTopeImposibleDaConflicto() {
		assertThatThrownBy(() -> SorteoDePlantilla.conTope(CUPOS, catalogo(), millones(10), new Random(1)))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(e.getReason()).contains("dentro del tope").contains("ya suman");
				});
	}

	@Test
	void sinCandidatosSuficientesDaConflictoEnLasDosModalidades() {
		Map<Position, List<Player>> pocos = catalogo();
		pocos.put(Position.DEFENSA, pocos.get(Position.DEFENSA).subList(0, 3));

		for (ThrowingSorteo sorteo : List.<ThrowingSorteo>of(
				() -> SorteoDePlantilla.aleatorio(CUPOS, pocos, new Random(1)),
				() -> SorteoDePlantilla.conTope(CUPOS, pocos, millones(100), new Random(1)))) {
			assertThatThrownBy(sorteo::ejecutar).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
				assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(e.getReason()).contains("No hay jugadores con valor").contains("DEFENSA");
			});
		}
	}

	private interface ThrowingSorteo {
		void ejecutar();
	}
}
