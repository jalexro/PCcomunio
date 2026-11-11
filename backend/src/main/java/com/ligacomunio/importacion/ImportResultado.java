package com.ligacomunio.importacion;

import java.util.List;

public record ImportResultado(String tarea, int guardados, int omitidos, Integer peticionesRestantesHoy, List<String> avisos) {
}
