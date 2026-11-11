package com.ligacomunio.valores;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Valor de mercado inventado para los jugadores de los que no hay precio real.
 *
 * <p>El archivo de Biwenger es de otra temporada y solo cubre parte de los equipos, así que al importar las
 * plantillas completas muchos jugadores se quedarían a 0 € y, por tanto, fuera del juego (no se pueden
 * fichar ni repartir). A esos se les asigna aquí un valor estable: siempre el mismo para el mismo jugador,
 * porque sale de un hash de su identificador y no de un sorteo. Si más tarde aparece su precio real en
 * Biwenger, ese precio manda ({@link ValueImportService} sobrescribe el valor generado).
 */
@Component
public class GeneradorDeValores {

	/** Valor típico del mejor jugador de cada posición; el peor ronda el 8 % de esta cifra. */
	private static final long REFERENCIA_PORTERO = 9_000_000L;
	private static final long REFERENCIA_DEFENSA = 12_000_000L;
	private static final long REFERENCIA_CENTROCAMPISTA = 14_000_000L;
	private static final long REFERENCIA_DELANTERO = 16_000_000L;

	private static final long REDONDEO = 50_000L;

	/** Valor en euros, múltiplo de 50.000 y siempre > 0 (si fuera 0 el jugador quedaría fuera del juego). */
	public BigDecimal valorPara(Player jugador) {
		double nivel = nivelEstable(jugador);
		long referencia = referencia(jugador.getPosition());
		long bruto = Math.round(referencia * (0.08 + 0.92 * nivel));
		long redondeado = Math.max(REDONDEO, (bruto / REDONDEO) * REDONDEO);
		return BigDecimal.valueOf(redondeado);
	}

	/**
	 * Número en [0,1) propio de cada jugador. Se mezcla el identificador con un multiplicador impar grande
	 * (hash de Fibonacci) y se usan los bits altos, que es lo que reparte de forma uniforme; usar los bajos
	 * daría valores casi iguales para identificadores consecutivos.
	 */
	private double nivelEstable(Player jugador) {
		long semilla = jugador.getExternalId() != null ? jugador.getExternalId() : jugador.getId();
		long mezclado = (semilla + 0x9E3779B97F4A7C15L) * -7046029254386353131L;
		return ((mezclado >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
	}

	private long referencia(Position posicion) {
		return switch (posicion) {
			case PORTERO -> REFERENCIA_PORTERO;
			case DEFENSA -> REFERENCIA_DEFENSA;
			case CENTROCAMPISTA -> REFERENCIA_CENTROCAMPISTA;
			case DELANTERO -> REFERENCIA_DELANTERO;
		};
	}
}
