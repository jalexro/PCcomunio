package com.ligacomunio.league;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Premio en euros por puesto en una jornada, guardado como lista separada por comas ("3000000,2000000,1000000"). */
final class PremiosPorPosicion {

	private PremiosPorPosicion() {
	}

	static List<BigDecimal> leer(String texto) {
		List<BigDecimal> premios = new ArrayList<>();
		if (texto == null || texto.isBlank()) {
			return premios;
		}
		for (String parte : texto.split(",")) {
			premios.add(new BigDecimal(parte.trim()));
		}
		return premios;
	}

	static String escribir(List<BigDecimal> premios) {
		return premios.stream().map(p -> p.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()).collect(Collectors.joining(","));
	}

	/** Premio del puesto (empieza en 1); 0 si ese puesto no tiene premio. */
	static BigDecimal premio(List<BigDecimal> premios, int posicion) {
		return posicion >= 1 && posicion <= premios.size() ? premios.get(posicion - 1) : BigDecimal.ZERO;
	}
}
