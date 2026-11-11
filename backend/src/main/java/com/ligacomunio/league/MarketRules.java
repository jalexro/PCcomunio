package com.ligacomunio.league;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Reglas de las ofertas: importe entero en euros entre el valor del jugador y el valor más el tope porcentual. */
public final class MarketRules {

	private MarketRules() {
	}

	public static BigDecimal minimo(BigDecimal valor) {
		return valor.setScale(0, RoundingMode.CEILING);
	}

	/** Valor + tope %, redondeado hacia abajo a euros enteros. */
	public static BigDecimal maximo(BigDecimal valor, int topePorcentaje) {
		return valor.multiply(BigDecimal.valueOf(100L + topePorcentaje))
				.divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
	}

	/** Devuelve null si el importe es válido, o el motivo en castellano. */
	public static String motivoDeRechazo(BigDecimal importe, BigDecimal valor, int topePorcentaje) {
		if (importe.stripTrailingZeros().scale() > 0) {
			return "El importe debe ser un número entero de euros";
		}
		BigDecimal min = minimo(valor);
		BigDecimal max = maximo(valor, topePorcentaje);
		if (importe.compareTo(min) < 0 || importe.compareTo(max) > 0) {
			return "La oferta debe estar entre " + formato(min) + " y " + formato(max) + " (el valor del jugador más un máximo del "
					+ topePorcentaje + " %)";
		}
		return null;
	}

	private static String formato(BigDecimal euros) {
		return String.format(java.util.Locale.forLanguageTag("es-ES"), "%,d €", euros.longValue());
	}
}
