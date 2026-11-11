package com.ligacomunio.match;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

	private final MatchService matchService;

	public MatchController(MatchService matchService) {
		this.matchService = matchService;
	}

	@GetMapping
	public List<MatchDto> listar(@RequestParam(required = false) Integer matchday) {
		return matchService.listar(matchday);
	}

	@GetMapping("/{id}")
	public MatchDto obtener(@PathVariable Long id) {
		return matchService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MatchDto crear(@Valid @RequestBody CrearPartidoRequest request) {
		return matchService.crear(request);
	}

	@PutMapping("/{id}/result")
	public MatchDto registrarResultado(@PathVariable Long id, @Valid @RequestBody ResultadoRequest request) {
		return matchService.registrarResultado(id, request);
	}

	@GetMapping("/{id}/stats")
	public List<EstadisticasDto> estadisticas(@PathVariable Long id) {
		return matchService.estadisticas(id);
	}

	/** Crea o reemplaza las estadísticas de un jugador; devuelve las estadísticas con los puntos calculados. */
	@PutMapping("/{id}/stats")
	public EstadisticasDto guardarEstadisticas(@PathVariable Long id, @Valid @RequestBody EstadisticasRequest request) {
		return matchService.guardarEstadisticas(id, request);
	}
}
