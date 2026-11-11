package com.ligacomunio.league;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ligacomunio.player.Position;

import java.util.Arrays;
import java.util.Collection;

/**
 * Tácticas disponibles. Todas suman 11 con el portero. En la base de datos se guarda el nombre de la
 * constante (F442) y en la API el código legible ("4-4-2").
 */
public enum Formation {
	F433("4-3-3", 4, 3, 3),
	F532("5-3-2", 5, 3, 2),
	F442("4-4-2", 4, 4, 2),
	F352("3-5-2", 3, 5, 2);

	public static final int TITULARES = 11;

	private final String codigo;
	private final int defensas;
	private final int medios;
	private final int delanteros;

	Formation(String codigo, int defensas, int medios, int delanteros) {
		this.codigo = codigo;
		this.defensas = defensas;
		this.medios = medios;
		this.delanteros = delanteros;
	}

	@JsonValue
	public String codigo() {
		return codigo;
	}

	@JsonCreator
	public static Formation deCodigo(String codigo) {
		return Arrays.stream(values()).filter(f -> f.codigo.equals(codigo)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Táctica no válida: " + codigo));
	}

	public int defensas() {
		return defensas;
	}

	public int medios() {
		return medios;
	}

	public int delanteros() {
		return delanteros;
	}

	/** Devuelve null si los titulares encajan con la táctica, o el motivo en castellano si no. */
	public String motivoDeRechazo(Collection<Position> titulares) {
		if (titulares.size() != TITULARES) {
			return "Hay que elegir exactamente " + TITULARES + " titulares";
		}
		long porteros = contar(titulares, Position.PORTERO);
		long defs = contar(titulares, Position.DEFENSA);
		long meds = contar(titulares, Position.CENTROCAMPISTA);
		long dels = contar(titulares, Position.DELANTERO);
		if (porteros != 1 || defs != defensas || meds != medios || dels != delanteros) {
			return "La táctica " + codigo + " necesita 1 portero, " + defensas + " defensas, " + medios
					+ " centrocampistas y " + delanteros + " delanteros (has elegido " + porteros + " POR, "
					+ defs + " DEF, " + meds + " MED y " + dels + " DEL)";
		}
		return null;
	}

	private static long contar(Collection<Position> posiciones, Position buscada) {
		return posiciones.stream().filter(p -> p == buscada).count();
	}
}
