package com.ligacomunio.importacion;

import com.ligacomunio.importacion.GuardadoImportacion.Guardado;
import com.ligacomunio.importacion.GuardadoImportacion.PartidoPendiente;
import com.ligacomunio.valores.ValueImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/** Orquesta la importación: llama al proveedor (sin transacción) y guarda con transacciones cortas. */
@Service
public class ImportService {

	private static final Logger log = LoggerFactory.getLogger(ImportService.class);

	private final StatsProvider proveedor;
	private final GuardadoImportacion guardado;
	private final ValueImportService valores;

	public ImportService(StatsProvider proveedor, GuardadoImportacion guardado, ValueImportService valores) {
		this.proveedor = proveedor;
		this.guardado = guardado;
		this.valores = valores;
	}

	public ImportResultado importarEquipos(int temporada) {
		int guardados = guardado.guardarEquipos(proveedor.equipos(temporada));
		return new ImportResultado("equipos", guardados, 0, proveedor.peticionesRestantesHoy(), List.of());
	}

	/**
	 * Catálogo completo de jugadores de la temporada (una petición por página de 20). Después se aplican los
	 * valores de mercado conocidos y se genera uno para los que no tengan, de modo que todos sean fichables.
	 */
	public ImportResultado importarPlantillas(int temporada) {
		Guardado resultado = guardado.guardarPlantillas(proveedor.plantillas(temporada));
		reaplicarValores();
		int generados = guardado.generarValoresQueFaltan();
		List<String> avisos = new ArrayList<>();
		if (resultado.omitidos() > 0) {
			avisos.add(resultado.omitidos() + " jugadores omitidos (equipo desconocido: importa antes los equipos)");
		}
		if (generados > 0) {
			avisos.add(generados + " jugadores sin precio real: se les ha generado un valor de mercado");
		}
		return new ImportResultado("plantillas", resultado.guardados(), resultado.omitidos(),
				proveedor.peticionesRestantesHoy(), avisos);
	}

	/** Requiere haber importado antes los equipos: los partidos con equipos desconocidos se omiten. */
	public ImportResultado importarPartidos(int temporada) {
		Guardado resultado = guardado.guardarPartidos(temporada, proveedor.partidos(temporada));
		List<String> avisos = resultado.omitidos() > 0
				? List.of(resultado.omitidos() + " partidos omitidos (equipo desconocido: importa antes los equipos)")
				: List.of();
		return new ImportResultado("partidos", resultado.guardados(), resultado.omitidos(),
				proveedor.peticionesRestantesHoy(), avisos);
	}

	/**
	 * Importa las estadísticas de los partidos finalizados de una jornada que aún no las tienen
	 * (1 petición por partido). Se detiene sin error si se agota la cuota.
	 */
	public ImportResultado importarJornada(int temporada, int jornada, int maximoPartidos) {
		List<PartidoPendiente> pendientes = guardado.partidosPendientes(temporada, jornada);
		List<String> avisos = new ArrayList<>();
		int guardados = 0;
		int omitidos = 0;
		int procesados = 0;

		for (PartidoPendiente partido : pendientes) {
			if (procesados >= maximoPartidos) {
				break;
			}
			try {
				Guardado g = guardado.guardarEstadisticas(partido.id(), proveedor.estadisticasPartido(partido.externalId()));
				guardados += g.guardados();
				omitidos += g.omitidos();
				procesados++;
			} catch (ResponseStatusException e) {
				if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
					throw e;
				}
				avisos.add("Importación detenida: " + e.getReason());
				break;
			}
		}
		if (guardados > 0) {
			reaplicarValores();
		}
		int sinImportar = pendientes.size() - procesados;
		if (sinImportar > 0 && avisos.isEmpty()) {
			avisos.add(sinImportar + " partidos de la jornada siguen pendientes: repite la llamada");
		}
		return new ImportResultado("jornada " + jornada + " (" + procesados + " partidos)", guardados, omitidos,
				proveedor.peticionesRestantesHoy(), avisos);
	}

	/** Los jugadores que acaba de crear la importación nacen sin valor: se les aplica el último archivo de valores. */
	private void reaplicarValores() {
		try {
			valores.reaplicar();
		} catch (RuntimeException e) {
			log.warn("No se pudieron reaplicar los valores de mercado tras importar la jornada", e);
		}
	}
}
