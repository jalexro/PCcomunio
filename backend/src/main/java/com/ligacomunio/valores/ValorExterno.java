package com.ligacomunio.valores;

import com.ligacomunio.player.PlayerStatus;
import com.ligacomunio.player.Position;

/** Valor de mercado y estado de un jugador tal como los da una fuente externa, sin depender de su formato. */
public record ValorExterno(String nombre, String slug, String equipo, Position posicion, long valor, PlayerStatus estado) {
}
