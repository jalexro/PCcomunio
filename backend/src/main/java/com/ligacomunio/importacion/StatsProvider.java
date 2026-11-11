package com.ligacomunio.importacion;

import java.util.List;

/** Fuente externa de datos de LaLiga. Cambiar de proveedor = otra implementación de esta interfaz. */
public interface StatsProvider {

	List<EquipoExterno> equipos(int temporada);

	/**
	 * Todos los jugadores inscritos en la competición esa temporada, con su equipo y su posición y sin
	 * estadísticas. Permite tener el catálogo completo antes de jugar la primera jornada, en vez de ir
	 * creando jugadores a medida que aparecen en los partidos.
	 */
	List<JugadorExterno> plantillas(int temporada);

	List<PartidoExterno> partidos(int temporada);

	List<EstadisticaExterna> estadisticasPartido(long partidoExternoId);

	/** Peticiones que quedan hoy en la cuenta del proveedor, o null si aún no se sabe. */
	Integer peticionesRestantesHoy();
}
