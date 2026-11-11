package com.ligacomunio.league;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Sorteo de la plantilla inicial de un miembro. Lógica pura (no toca la base de datos): recibe los cupos por
 * posición y los jugadores libres de cada posición, y devuelve los 11 elegidos.
 */
final class SorteoDePlantilla {

	private static final NumberFormat EUROS = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES"));

	private SorteoDePlantilla() {
	}

	/** Reparto de siempre: para cada posición, los que hagan falta al azar sin mirar el precio. */
	static List<Player> aleatorio(Map<Position, Integer> cupos, Map<Position, List<Player>> candidatos, Random azar) {
		List<Player> elegidos = new ArrayList<>();
		cupos.forEach((posicion, cuantos) -> {
			List<Player> disponibles = new ArrayList<>(candidatos.getOrDefault(posicion, List.of()));
			exigirSuficientes(disponibles.size(), cuantos, posicion);
			Collections.shuffle(disponibles, azar);
			elegidos.addAll(disponibles.subList(0, cuantos));
		});
		return elegidos;
	}

	/**
	 * Reparto con tope: 11 jugadores al azar cuya suma de valores no pasa de {@code tope}.
	 *
	 * <p>Estrategia (y por qué): no vale coger los 11 más baratos (saldría la misma plantilla en todas las
	 * ligas) ni sortear a ciegas y confiar en que la suma cuadre (fallaría casi siempre). Se sortean los 11
	 * puestos <strong>uno a uno y en orden aleatorio</strong> (así ninguna línea se queda sistemáticamente con
	 * las sobras) y antes de cada elección se calculan dos límites:</p>
	 * <ul>
	 *   <li><em>Reserva</em>: lo que como mínimo costarán los puestos que aún faltan (la suma de los más
	 *   baratos que quedan libres para cada uno). El jugador de este puesto no puede pasar de
	 *   {@code tope - gastado - reserva}, así que <strong>siempre queda margen para completar los 11</strong>:
	 *   el sorteo no puede quedarse a medias ni pasarse del tope.</li>
	 *   <li><em>Cuota</em>: de ese margen libre se ofrece a este puesto una parte al azar en torno a su parte
	 *   proporcional ({@code 1 / puestos que faltan}). Sin esto el primer puesto podría llevarse casi todo el
	 *   tope y el resto serían los más baratos; con esto el gasto se reparte entre las líneas y la plantilla
	 *   acaba cerca del tope, pero nunca sale la misma dos veces.</li>
	 * </ul>
	 * <p>Dentro de lo que permiten esos dos límites, el jugador se elige al azar con probabilidad uniforme.</p>
	 */
	static List<Player> conTope(Map<Position, Integer> cupos, Map<Position, List<Player>> candidatos, BigDecimal tope,
	                            Random azar) {
		Map<Position, List<Player>> libres = new EnumMap<>(Position.class);
		cupos.forEach((posicion, cuantos) -> {
			List<Player> disponibles = new ArrayList<>(candidatos.getOrDefault(posicion, List.of()));
			exigirSuficientes(disponibles.size(), cuantos, posicion);
			disponibles.sort(Comparator.comparing(Player::getMarketValue).thenComparing(Player::getId));
			libres.put(posicion, disponibles);
		});

		// Puestos pendientes (una entrada por jugador que hay que sortear) en orden aleatorio.
		List<Position> puestos = new ArrayList<>();
		cupos.forEach((posicion, cuantos) -> {
			for (int i = 0; i < cuantos; i++) {
				puestos.add(posicion);
			}
		});
		Collections.shuffle(puestos, azar);

		BigDecimal minimoPosible = reserva(libres, puestos);
		if (minimoPosible.compareTo(tope) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay jugadores con valor de mercado baratos "
					+ "suficientes para formar una plantilla dentro del tope de " + euros(tope)
					+ ": los 11 más baratos que quedan libres ya suman " + euros(minimoPosible)
					+ ". Sube el tope de la liga o espera a que se liberen jugadores.");
		}

		List<Player> elegidos = new ArrayList<>();
		BigDecimal gastado = BigDecimal.ZERO;
		for (int i = 0; i < puestos.size(); i++) {
			Position posicion = puestos.get(i);
			List<Position> pendientes = puestos.subList(i + 1, puestos.size());
			BigDecimal maximo = tope.subtract(gastado).subtract(reserva(libres, pendientes));
			Player elegido = sortearHasta(libres.get(posicion), maximo, pendientes.size() + 1, azar);
			elegidos.add(elegido);
			gastado = gastado.add(elegido.getMarketValue());
			libres.get(posicion).remove(elegido);
		}
		return elegidos;
	}

	/**
	 * Elige al azar uno de los candidatos (ordenados por valor) que no pase de {@code maximo}, limitando el
	 * gasto a la cuota de este puesto. {@code puestosRestantes} incluye el que se está sorteando.
	 */
	private static Player sortearHasta(List<Player> candidatos, BigDecimal maximo, int puestosRestantes, Random azar) {
		int cabenHastaMaximo = cuantosCaben(candidatos, maximo);
		// El más barato siempre cabe: la reserva le había guardado su sitio.
		BigDecimal minimo = candidatos.get(0).getMarketValue();
		// Cuota = el mínimo de este puesto + una parte al azar del margen libre, centrada en su parte
		// proporcional (de ahí el 2: el valor medio de un aleatorio en [0, 2/n] es 1/n).
		double parte = Math.min(1.0, azar.nextDouble() * 2.0 / puestosRestantes);
		BigDecimal cuota = minimo.add(maximo.subtract(minimo).multiply(BigDecimal.valueOf(parte)));
		int cuantos = Math.max(1, cuantosCaben(candidatos.subList(0, cabenHastaMaximo), cuota));
		return candidatos.get(azar.nextInt(cuantos));
	}

	/** Cuántos de los candidatos (ordenados de más barato a más caro) no pasan del límite. */
	private static int cuantosCaben(List<Player> candidatos, BigDecimal limite) {
		int cuantos = 0;
		while (cuantos < candidatos.size() && candidatos.get(cuantos).getMarketValue().compareTo(limite) <= 0) {
			cuantos++;
		}
		return cuantos;
	}

	/** Lo mínimo que costarán los puestos indicados: los más baratos que quedan libres para cada uno. */
	private static BigDecimal reserva(Map<Position, List<Player>> libres, List<Position> puestos) {
		Map<Position, Integer> cuantos = new LinkedHashMap<>();
		puestos.forEach(p -> cuantos.merge(p, 1, Integer::sum));
		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<Position, Integer> entrada : cuantos.entrySet()) {
			List<Player> disponibles = libres.get(entrada.getKey());
			for (int i = 0; i < entrada.getValue(); i++) {
				total = total.add(disponibles.get(i).getMarketValue());
			}
		}
		return total;
	}

	private static void exigirSuficientes(int disponibles, int cuantos, Position posicion) {
		if (disponibles < cuantos) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"No hay jugadores con valor de mercado suficientes para formar la plantilla (faltan " + posicion + ")");
		}
	}

	private static String euros(BigDecimal cantidad) {
		return EUROS.format(cantidad) + " €";
	}
}
