package com.ligacomunio.simulacion;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventa las estadísticas de un partido. Es lógica pura: no toca la base de datos ni el reloj, así que se
 * puede probar sola y repetir tantas veces como se quiera con el mismo resultado.
 *
 * <p>Por orden: se elige la alineación de cada equipo (los mejores por valor de mercado, con algo de
 * rotación), se decide el resultado (si el partido ya tiene marcador real se respeta y solo se inventa quién
 * marcó), se reparten goles y asistencias entre los jugadores y por último se rellena el resto de datos de
 * cada uno. Los puntos no se calculan aquí: los pone el {@code ScoringService} a partir de estos datos
 * brutos, exactamente igual que con los partidos importados de verdad.
 */
@Component
public class SimuladorDePartido {

	/** Valor de mercado a partir del cual se considera a un jugador del máximo nivel. */
	private static final double VALOR_REFERENCIA = 15_000_000d;
	private static final double NIVEL_MINIMO = 0.05;

	/** Titulares por posición: la simulación juega siempre un 4-4-2 clásico. */
	private static final Map<Position, Integer> TITULARES_POR_POSICION = new EnumMap<>(Map.of(
			Position.PORTERO, 1, Position.DEFENSA, 4, Position.CENTROCAMPISTA, 4, Position.DELANTERO, 2));
	private static final int TITULARES = 11;
	private static final int SUPLENTES = 7;

	/** Reparto de los goles y las asistencias de un equipo entre sus titulares, por posición. */
	private static final Map<Position, Double> PESO_GOL = new EnumMap<>(Map.of(
			Position.PORTERO, 0.01, Position.DEFENSA, 0.12, Position.CENTROCAMPISTA, 0.30, Position.DELANTERO, 0.57));
	private static final Map<Position, Double> PESO_ASISTENCIA = new EnumMap<>(Map.of(
			Position.PORTERO, 0.02, Position.DEFENSA, 0.25, Position.CENTROCAMPISTA, 0.48, Position.DELANTERO, 0.25));

	/** Lo que hizo un jugador en el partido; mismos conceptos que {@code PlayerMatchStats}. */
	public record Actuacion(
			Player jugador, int minutos, int goles, int asistencias, int entradas, int amarillas, int rojas,
			int pasesCompletados, int pasesFallados, int tirosFallados, int paradas, int golesEncajados,
			int tirosRecibidos, BigDecimal rating) {
	}

	public record PartidoSimulado(List<Actuacion> actuaciones, int golesLocal, int golesVisitante) {
	}

	/**
	 * @param golesLocalReales marcador real del partido si se conoce (los de LaLiga 2024/25 lo tienen); con
	 *                         null se inventa también el resultado
	 */
	public PartidoSimulado simular(long semillaGlobal, long partidoId, List<Player> local, List<Player> visitante,
	                               Integer golesLocalReales, Integer golesVisitanteReales) {
		Alineacion alineacionLocal = alinear(semillaGlobal, partidoId, local, true);
		Alineacion alineacionVisitante = alinear(semillaGlobal, partidoId, visitante, false);

		int golesLocal;
		int golesVisitante;
		if (golesLocalReales != null && golesVisitanteReales != null) {
			golesLocal = Math.max(0, golesLocalReales);
			golesVisitante = Math.max(0, golesVisitanteReales);
		} else {
			Dados dados = Dados.para(semillaGlobal, partidoId, 7L);
			// Ventaja de campo: al local se le sube un 15 % la media de goles.
			golesLocal = dados.poisson(1.15 * mediaDeGoles(alineacionLocal, alineacionVisitante));
			golesVisitante = dados.poisson(mediaDeGoles(alineacionVisitante, alineacionLocal));
		}

		List<Actuacion> actuaciones = new ArrayList<>();
		actuaciones.addAll(actuacionesDeEquipo(semillaGlobal, partidoId, alineacionLocal, true, golesLocal,
				golesVisitante, fuerza(alineacionVisitante)));
		actuaciones.addAll(actuacionesDeEquipo(semillaGlobal, partidoId, alineacionVisitante, false, golesVisitante,
				golesLocal, fuerza(alineacionLocal)));
		return new PartidoSimulado(actuaciones, golesLocal, golesVisitante);
	}

	// --- Alineación ---

	private record Alineacion(List<Player> titulares, List<Player> suplentes) {
	}

	/**
	 * Titulares y suplentes del equipo. Cada posición se ordena por nivel más un ruido propio del partido:
	 * los mejores juegan casi siempre, pero hay rotación de una jornada a otra. Si al equipo le faltan
	 * jugadores de alguna posición, el hueco lo ocupa el mejor de los que queden, para no simular un 4-4-2
	 * imposible con plantillas incompletas.
	 */
	private Alineacion alinear(long semillaGlobal, long partidoId, List<Player> plantilla, boolean esLocal) {
		Dados dados = Dados.para(semillaGlobal, partidoId, esLocal ? 1L : 2L);
		Map<Long, Double> fuerzaDelDia = new HashMap<>();
		for (Player p : plantilla) {
			fuerzaDelDia.put(p.getId(), nivel(p) + dados.fraccion() * 0.30);
		}
		Comparator<Player> mejorPrimero = Comparator
				.comparingDouble((Player p) -> fuerzaDelDia.getOrDefault(p.getId(), 0d)).reversed();

		List<Player> disponibles = new ArrayList<>(plantilla);
		disponibles.sort(mejorPrimero);

		List<Player> titulares = new ArrayList<>();
		for (Map.Entry<Position, Integer> hueco : TITULARES_POR_POSICION.entrySet()) {
			disponibles.stream().filter(p -> p.getPosition() == hueco.getKey()).limit(hueco.getValue())
					.forEach(titulares::add);
		}
		disponibles.removeAll(titulares);
		while (titulares.size() < TITULARES && !disponibles.isEmpty()) {
			titulares.add(disponibles.remove(0));
		}
		List<Player> suplentes = new ArrayList<>(disponibles.subList(0, Math.min(SUPLENTES, disponibles.size())));
		return new Alineacion(titulares, suplentes);
	}

	private double nivel(Player jugador) {
		double valor = jugador.getMarketValue() == null ? 0 : jugador.getMarketValue().doubleValue();
		return Math.clamp(valor / VALOR_REFERENCIA, NIVEL_MINIMO, 1.0);
	}

	/** Nivel medio de los titulares: lo que mide la fuerza del equipo en este partido. */
	private double fuerza(Alineacion alineacion) {
		return alineacion.titulares().isEmpty() ? NIVEL_MINIMO
				: alineacion.titulares().stream().mapToDouble(this::nivel).average().orElse(NIVEL_MINIMO);
	}

	/** Entre 0,6 y ~2,6 goles de media, según lo mejor que sea el equipo respecto al rival. */
	private double mediaDeGoles(Alineacion atacante, Alineacion defensor) {
		return 0.6 + 2.0 * fuerza(atacante) * (1.0 - 0.5 * fuerza(defensor));
	}

	// --- Actuaciones ---

	private List<Actuacion> actuacionesDeEquipo(long semillaGlobal, long partidoId, Alineacion alineacion,
	                                            boolean esLocal, int golesAFavor, int golesEnContra,
	                                            double fuerzaRival) {
		List<Player> titulares = alineacion.titulares();
		Dados dados = Dados.para(semillaGlobal, partidoId, esLocal ? 11L : 12L, 3L);

		Map<Long, Integer> minutos = repartirMinutos(dados, alineacion);
		Map<Long, Integer> goles = repartirEntreTitulares(dados, titulares, golesAFavor, PESO_GOL);
		// Uno de cada tres goles no lleva asistencia (jugada individual, penalti, rechace).
		int asistenciasTotales = 0;
		for (int i = 0; i < golesAFavor; i++) {
			if (dados.suerte(0.66)) {
				asistenciasTotales++;
			}
		}
		Map<Long, Integer> asistencias = repartirEntreTitulares(dados, titulares, asistenciasTotales, PESO_ASISTENCIA);

		List<Actuacion> actuaciones = new ArrayList<>();
		List<Player> convocados = new ArrayList<>(titulares);
		convocados.addAll(alineacion.suplentes());
		for (Player jugador : convocados) {
			int jugados = minutos.getOrDefault(jugador.getId(), 0);
			if (jugados <= 0) {
				// Suplente que se queda en el banquillo: no genera estadística, igual que en la importación real.
				continue;
			}
			actuaciones.add(actuacion(semillaGlobal, partidoId, jugador, jugados,
					goles.getOrDefault(jugador.getId(), 0), asistencias.getOrDefault(jugador.getId(), 0),
					golesEnContra, fuerzaRival));
		}
		return actuaciones;
	}

	/** Entre 2 y 4 cambios: el que sale deja de jugar en el minuto del cambio y el portero nunca se cambia. */
	private Map<Long, Integer> repartirMinutos(Dados dados, Alineacion alineacion) {
		Map<Long, Integer> minutos = new HashMap<>();
		alineacion.titulares().forEach(p -> minutos.put(p.getId(), 90));

		List<Player> sustituibles = new ArrayList<>(alineacion.titulares().stream()
				.filter(p -> p.getPosition() != Position.PORTERO).toList());
		int cambios = Math.min(dados.entero(2, 4), Math.min(sustituibles.size(), alineacion.suplentes().size()));
		for (int i = 0; i < cambios; i++) {
			Player sale = sustituibles.remove(dados.entero(0, sustituibles.size() - 1));
			int minuto = dados.entero(55, 85);
			minutos.put(sale.getId(), minuto);
			minutos.put(alineacion.suplentes().get(i).getId(), 90 - minuto);
		}
		return minutos;
	}

	/** Reparte goles (o asistencias) entre los titulares según su posición y su nivel. */
	private Map<Long, Integer> repartirEntreTitulares(Dados dados, List<Player> titulares, int total,
	                                                  Map<Position, Double> pesosPorPosicion) {
		Map<Long, Integer> reparto = new HashMap<>();
		if (total <= 0 || titulares.isEmpty()) {
			return reparto;
		}
		double[] pesos = new double[titulares.size()];
		for (int i = 0; i < titulares.size(); i++) {
			Player p = titulares.get(i);
			pesos[i] = pesosPorPosicion.getOrDefault(p.getPosition(), 0.1) * (0.4 + nivel(p));
		}
		int[] cuentas = dados.repartir(total, pesos);
		for (int i = 0; i < titulares.size(); i++) {
			if (cuentas[i] > 0) {
				reparto.put(titulares.get(i).getId(), cuentas[i]);
			}
		}
		return reparto;
	}

	/** Rellena el resto de datos de un jugador: pases, entradas, tiros, tarjetas y, si es portero, paradas. */
	private Actuacion actuacion(long semillaGlobal, long partidoId, Player jugador, int minutos, int goles,
	                            int asistencias, int golesEnContra, double fuerzaRival) {
		Dados dados = Dados.para(semillaGlobal, partidoId, jugador.getId(), 4L);
		double nivel = nivel(jugador);
		double parteJugada = minutos / 90.0;
		boolean portero = jugador.getPosition() == Position.PORTERO;

		int amarillas = dados.suerte(probabilidadDeAmarilla(jugador.getPosition()) * parteJugada) ? 1 : 0;
		int rojas = dados.suerte(0.012 * parteJugada) ? 1 : 0;

		int entradas = portero ? 0
				: dados.poisson(mediaDeEntradas(jugador.getPosition()) * (0.5 + nivel) * parteJugada);
		int tirosFallados = portero ? 0
				: dados.poisson(mediaDeTiros(jugador.getPosition()) * (0.5 + nivel) * parteJugada);

		int pasesTotales = (int) Math.round(mediaDePases(jugador.getPosition()) * (0.6 + 0.8 * nivel) * parteJugada);
		pasesTotales = Math.max(0, pasesTotales + dados.entero(-6, 6));
		// Acierto entre el 68 % y el 92 % según el nivel, con algo de variación por partido.
		double acierto = Math.clamp(0.68 + 0.20 * nivel + (dados.fraccion() - 0.5) * 0.08, 0.5, 0.98);
		int pasesCompletados = (int) Math.round(pasesTotales * acierto);

		int paradas = 0;
		int golesEncajados = 0;
		int tirosRecibidos = 0;
		if (portero) {
			golesEncajados = (int) Math.round(golesEnContra * parteJugada);
			// Cuanto mejor es el rival, más tiros llegan; las paradas son los que no acabaron en gol.
			paradas = dados.poisson((1.2 + 3.0 * fuerzaRival) * parteJugada);
			tirosRecibidos = paradas + golesEncajados;
		}

		return new Actuacion(jugador, minutos, goles, asistencias, entradas, amarillas, rojas, pasesCompletados,
				Math.max(0, pasesTotales - pasesCompletados), tirosFallados, paradas, golesEncajados, tirosRecibidos,
				rating(goles, asistencias, amarillas, rojas, golesEncajados, paradas, nivel, parteJugada));
	}

	private double probabilidadDeAmarilla(Position posicion) {
		return switch (posicion) {
			case PORTERO -> 0.03;
			case DEFENSA -> 0.20;
			case CENTROCAMPISTA -> 0.18;
			case DELANTERO -> 0.12;
		};
	}

	private double mediaDeEntradas(Position posicion) {
		return switch (posicion) {
			case PORTERO -> 0;
			case DEFENSA -> 2.2;
			case CENTROCAMPISTA -> 1.8;
			case DELANTERO -> 0.8;
		};
	}

	private double mediaDeTiros(Position posicion) {
		return switch (posicion) {
			case PORTERO -> 0;
			case DEFENSA -> 0.4;
			case CENTROCAMPISTA -> 1.0;
			case DELANTERO -> 1.8;
		};
	}

	private double mediaDePases(Position posicion) {
		return switch (posicion) {
			case PORTERO -> 24;
			case DEFENSA -> 52;
			case CENTROCAMPISTA -> 48;
			case DELANTERO -> 24;
		};
	}

	/** Nota de 4,0 a 9,5, al estilo de la que da la API real. Es informativa: no entra en la puntuación. */
	private BigDecimal rating(int goles, int asistencias, int amarillas, int rojas, int golesEncajados, int paradas,
	                          double nivel, double parteJugada) {
		double nota = 6.1 + 0.6 * nivel + 0.9 * goles + 0.5 * asistencias - 0.3 * amarillas - 1.2 * rojas
				+ 0.15 * paradas - 0.35 * golesEncajados - (1 - parteJugada) * 0.3;
		return BigDecimal.valueOf(Math.clamp(nota, 4.0, 9.5)).setScale(1, RoundingMode.HALF_UP);
	}
}
