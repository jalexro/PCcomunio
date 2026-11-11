package com.ligacomunio.importacion;

/** Equipo tal como lo describe una fuente externa, independiente de cuál sea. */
public record EquipoExterno(long id, String nombre, String codigo, String escudo) {
}
