package com.ligacomunio.simulacion;

import com.ligacomunio.simulacion.SimuladorDeJornada.ResultadoSimulacion;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparadores de la simulación (solo ADMIN, como el resto de /api/admin/**). No hace falta usarlos para
 * jugar: el avance de jornada simula sola la jornada que le toque. Sirven para dejar la temporada entera
 * preparada de golpe.
 */
@RestController
@RequestMapping("/api/admin/simulate")
public class SimulacionController {

	private final SimuladorDeJornada simulador;

	public SimulacionController(SimuladorDeJornada simulador) {
		this.simulador = simulador;
	}

	@PostMapping("/matchdays/{jornada}")
	public ResultadoSimulacion jornada(@PathVariable int jornada, @RequestParam(defaultValue = "2024") int season) {
		return simulador.simularJornada(season, jornada);
	}

	@PostMapping("/season")
	public ResultadoSimulacion temporada(@RequestParam(defaultValue = "2024") int season,
	                                     @RequestParam(defaultValue = "38") int hasta) {
		return simulador.simularHasta(season, hasta);
	}
}
