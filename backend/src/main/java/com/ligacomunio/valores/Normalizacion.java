package com.ligacomunio.valores;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Utilidades para comparar nombres de personas y equipos venidos de fuentes distintas. */
final class Normalizacion {

	private Normalizacion() {
	}

	/** Minúsculas, sin tildes ni signos, con un solo espacio entre palabras. */
	static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	static List<String> palabras(String texto) {
		String normalizado = normalizar(texto);
		return normalizado.isEmpty() ? List.of() : Arrays.asList(normalizado.split(" "));
	}

	/** Dos nombres de equipo encajan si son iguales o uno contiene al otro como palabras completas. */
	static boolean equipoCompatible(String a, String b) {
		String na = normalizar(a);
		String nb = normalizar(b);
		if (na.isEmpty() || nb.isEmpty()) {
			return false;
		}
		return na.equals(nb) || (" " + na + " ").contains(" " + nb + " ") || (" " + nb + " ").contains(" " + na + " ");
	}
}
