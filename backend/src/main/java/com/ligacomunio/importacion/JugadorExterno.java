package com.ligacomunio.importacion;

import com.ligacomunio.player.Position;

/**
 * Jugador de una plantilla, sin estadísticas: lo que hace falta para tener el catálogo completo de la
 * temporada antes de que se juegue (o se simule) ninguna jornada.
 */
public record JugadorExterno(long id, String nombre, String foto, long equipoId, Position posicion) {
}
