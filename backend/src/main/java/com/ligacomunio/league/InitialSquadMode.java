package com.ligacomunio.league;

/**
 * Cómo se reparte la plantilla inicial a cada miembro que entra en la liga. Lo elige quien crea la liga y se
 * guarda en ella, porque quien se une más tarde tiene que recibir exactamente el mismo trato.
 */
public enum InitialSquadMode {
	/** 11 jugadores al azar (1 POR, 4 DEF, 4 MED, 2 DEL) sin mirar el precio, y el presupuesto elegido. */
	ALEATORIO,
	/** Ningún jugador: se empieza con la plantilla vacía y hay que fichar todo en el mercado. */
	SIN_JUGADORES,
	/** 11 jugadores al azar cuya suma de valores no pasa del tope de la liga ({@code initialSquadCap}). */
	ALEATORIO_CON_TOPE
}
