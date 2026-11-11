package com.ligacomunio.valores;

import java.util.List;

/**
 * Informe de cobertura de una importación de valores.
 *
 * @param enArchivo               jugadores con valor en el archivo (sin entrenadores)
 * @param emparejados             jugadores de la base de datos a los que se ha puesto valor
 * @param emparejadosEnOtroEquipo de ellos, los que se encontraron por nombre completo aunque el equipo no coincida (fichajes)
 * @param sinEmparejar            jugadores del archivo que no encajan con ninguno de la base de datos
 * @param equiposSinCorrespondencia equipos del archivo que no existen en la base de datos
 * @param jugadoresSinValor       jugadores de la base de datos que siguen sin valor (quedan fuera del juego)
 */
public record ValoresResultado(
		int enArchivo,
		int emparejados,
		int emparejadosEnOtroEquipo,
		List<String> sinEmparejar,
		List<String> equiposSinCorrespondencia,
		List<String> jugadoresSinValor) {
}
