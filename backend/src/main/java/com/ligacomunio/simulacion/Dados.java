package com.ligacomunio.simulacion;

import java.util.Random;

/**
 * Azar reproducible. Toda la simulación saca sus números de aquí y la semilla se construye a partir de
 * identificadores estables (partido, equipo, jugador), nunca del reloj: simular dos veces la misma jornada
 * da exactamente el mismo resultado y todos los miembros de todas las ligas ven los mismos puntos.
 *
 * <p>{@code java.util.Random} tiene un algoritmo especificado en el lenguaje, así que la misma semilla da la
 * misma secuencia en cualquier máquina y versión de Java.
 */
final class Dados {

	private final Random azar;

	private Dados(long semilla) {
		this.azar = new Random(semilla);
	}

	/** Mezcla los identificadores con la semilla global de la simulación. */
	static Dados para(long semillaGlobal, long... partes) {
		long semilla = semillaGlobal * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
		for (long parte : partes) {
			// Multiplicar por un primo grande antes de sumar evita que (1,2) y (2,1) acaben en la misma semilla.
			semilla = semilla * 1099511628211L + parte;
		}
		return new Dados(semilla);
	}

	/** Número en [0,1). */
	double fraccion() {
		return azar.nextDouble();
	}

	/** True con probabilidad p (p<=0 nunca, p>=1 siempre). */
	boolean suerte(double p) {
		return azar.nextDouble() < p;
	}

	/** Entero en [desde, hasta], ambos incluidos. */
	int entero(int desde, int hasta) {
		return hasta <= desde ? desde : desde + azar.nextInt(hasta - desde + 1);
	}

	/**
	 * Variable de Poisson por el método de Knuth: cuenta sucesos independientes en un intervalo, que es
	 * justo la forma de los goles, las entradas o las tarjetas de un partido. Con medias pequeñas (todas
	 * las de aquí) el bucle da una o dos vueltas.
	 */
	int poisson(double media) {
		if (media <= 0) {
			return 0;
		}
		double limite = Math.exp(-Math.min(media, 20));
		double producto = azar.nextDouble();
		int k = 0;
		while (producto > limite && k < 20) {
			k++;
			producto *= azar.nextDouble();
		}
		return k;
	}

	/** Reparte un entero entre varios pesos, respetando el total exacto. */
	int[] repartir(int total, double[] pesos) {
		int[] reparto = new int[pesos.length];
		double suma = 0;
		for (double peso : pesos) {
			suma += peso;
		}
		for (int i = 0; i < total; i++) {
			if (suma <= 0) {
				reparto[azar.nextInt(pesos.length)]++;
				continue;
			}
			double dardo = azar.nextDouble() * suma;
			double acumulado = 0;
			for (int j = 0; j < pesos.length; j++) {
				acumulado += pesos[j];
				if (dardo <= acumulado || j == pesos.length - 1) {
					reparto[j]++;
					break;
				}
			}
		}
		return reparto;
	}
}
