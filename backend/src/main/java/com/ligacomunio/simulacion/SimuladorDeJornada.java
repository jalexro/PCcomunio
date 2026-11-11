package com.ligacomunio.simulacion;

import com.ligacomunio.match.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Simula las jornadas que no tienen estadísticas. Sustituye a la importación como forma normal de que una
 * jornada esté lista para jugarse: no gasta cuota de ninguna API, es instantánea y permite llegar a la
 * jornada 38 sin depender de un plan de pago.
 *
 * <p>No es transaccional a propósito: cada partido se guarda en su propia transacción corta
 * ({@link GuardadoSimulacion}), así simular una jornada de diez partidos no bloquea nada durante todo el
 * proceso. Los partidos que ya tienen estadísticas (importadas de verdad) se dejan intactos.
 */
@Service
public class SimuladorDeJornada {

	private static final Logger log = LoggerFactory.getLogger(SimuladorDeJornada.class);

	/** Cuántos partidos ha simulado y cuántas estadísticas de jugador ha escrito. */
	public record ResultadoSimulacion(int partidos, int estadisticas) {
	}

	private final MatchRepository matchRepository;
	private final GuardadoSimulacion guardado;
	private final long semilla;

	public SimuladorDeJornada(MatchRepository matchRepository, GuardadoSimulacion guardado,
	                          @Value("${app.simulacion.semilla:2024}") long semilla) {
		this.matchRepository = matchRepository;
		this.guardado = guardado;
		this.semilla = semilla;
	}

	/** Simula los partidos de una jornada que aún no tengan estadísticas. */
	public ResultadoSimulacion simularJornada(int temporada, int jornada) {
		List<Long> partidos = matchRepository.idsSinEstadisticas(temporada, jornada);
		int estadisticas = 0;
		int simulados = 0;
		for (Long partidoId : partidos) {
			int escritas = guardado.simularPartido(partidoId, semilla);
			if (escritas > 0) {
				simulados++;
				estadisticas += escritas;
			}
		}
		if (simulados > 0) {
			log.info("Jornada {} de {} simulada: {} partidos y {} estadísticas", jornada, temporada, simulados, estadisticas);
		}
		return new ResultadoSimulacion(simulados, estadisticas);
	}

	/** Simula de la jornada 1 a la indicada, para dejar la temporada lista de una vez. */
	public ResultadoSimulacion simularHasta(int temporada, int jornadaFinal) {
		Integer ultima = matchRepository.ultimaJornada(temporada);
		int tope = Math.min(jornadaFinal, ultima == null ? 0 : ultima);
		int partidos = 0;
		int estadisticas = 0;
		for (int jornada = 1; jornada <= tope; jornada++) {
			ResultadoSimulacion parcial = simularJornada(temporada, jornada);
			partidos += parcial.partidos();
			estadisticas += parcial.estadisticas();
		}
		return new ResultadoSimulacion(partidos, estadisticas);
	}
}
