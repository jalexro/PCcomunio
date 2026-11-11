package com.ligacomunio.importacion;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparadores manuales de la importación. Cada llamada gasta cuota de la API externa y escribe en la
 * base de datos; hoy no hay autenticación, así que hay que protegerlos cuando exista el login.
 */
@RestController
@RequestMapping("/api/admin/import")
public class ImportController {

	private final ImportService importService;

	public ImportController(ImportService importService) {
		this.importService = importService;
	}

	@PostMapping("/teams")
	public ImportResultado equipos(@RequestParam(defaultValue = "2024") int season) {
		return importService.importarEquipos(season);
	}

	@PostMapping("/squads")
	public ImportResultado plantillas(@RequestParam(defaultValue = "2024") int season) {
		return importService.importarPlantillas(season);
	}

	@PostMapping("/fixtures")
	public ImportResultado partidos(@RequestParam(defaultValue = "2024") int season) {
		return importService.importarPartidos(season);
	}

	@PostMapping("/matchdays/{jornada}")
	public ImportResultado jornada(@PathVariable int jornada,
	                               @RequestParam(defaultValue = "2024") int season,
	                               @RequestParam(defaultValue = "10") int max) {
		return importService.importarJornada(season, jornada, max);
	}
}
