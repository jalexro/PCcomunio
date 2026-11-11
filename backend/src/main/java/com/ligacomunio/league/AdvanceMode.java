package com.ligacomunio.league;

/** Cuándo se juega la jornada siguiente de una liga. */
public enum AdvanceMode {
	/** Cuando vence el reloj: pasado {@code matchdayIntervalSeconds} desde la jornada anterior. */
	POR_TIEMPO,
	/**
	 * En cuanto todos los miembros confirman su alineación. {@code nextAdvanceAt} actúa de fecha límite:
	 * si alguien no confirma a tiempo, se juega con la alineación que tenga puesta.
	 */
	POR_CONFIRMACION
}
