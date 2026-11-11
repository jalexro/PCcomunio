package com.ligacomunio.league;

import com.ligacomunio.simulacion.SimuladorDeJornada;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Única puerta de entrada al avance de jornada: si a la jornada siguiente le faltan estadísticas, las simula
 * y después puntúa con {@link MatchdayService}. Lo usan el reloj ({@link LeagueScheduler}), el botón del
 * dueño y la confirmación del último miembro que faltaba.
 *
 * <p>No es transaccional a propósito: la simulación escribe con una transacción por partido y no debe
 * mantener bloqueada la liga mientras lo hace.
 *
 * <p>Antes esto importaba las estadísticas de API-Football y por eso el reloj no podía usarlo (habría
 * gastado cuota sin que nadie lo pidiera). Al simular ya no hay cuota ni espera, así que el reloj puede
 * preparar la jornada él solo y la temporada entera es jugable hasta la jornada 38.
 */
@Service
public class AvanceDeJornada {

	/** Solo una simulación a la vez: dos ligas avanzando a la par no deben simular el mismo partido dos veces. */
	private final Object simulacionEnCurso = new Object();

	private final LeagueRepository leagueRepository;
	private final MatchdayService matchdayService;
	private final SimuladorDeJornada simulador;

	public AvanceDeJornada(LeagueRepository leagueRepository, MatchdayService matchdayService,
	                       SimuladorDeJornada simulador) {
		this.leagueRepository = leagueRepository;
		this.matchdayService = matchdayService;
		this.simulador = simulador;
	}

	/**
	 * @param automatico true cuando lo dispara el reloj o una confirmación: entonces no se avanza si todavía
	 *                   no toca, en vez de dar error. El dueño lo llama con false para avanzar a mano.
	 */
	public void avanzar(Long leagueId, boolean automatico) {
		League liga = leagueRepository.findById(leagueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La liga no existe"));
		if (liga.getStatus() == LeagueStatus.EN_CURSO) {
			// Con automatico se comprueba antes de simular para no generar la jornada siguiente en cada ciclo
			// del reloj. La condición se vuelve a mirar en MatchdayService, ya con la liga bloqueada.
			if (automatico && !matchdayService.tocaAvanzar(liga)) {
				return;
			}
			prepararJornada(liga.getSeason(), liga.getCurrentMatchday() + 1);
		}
		matchdayService.avanzar(leagueId, automatico);
	}

	/** Simula los partidos de la jornada que no tengan estadísticas. Los importados de verdad no se tocan. */
	private void prepararJornada(int temporada, int jornada) {
		synchronized (simulacionEnCurso) {
			if (matchdayService.jornadaLista(temporada, jornada)) {
				return;
			}
			simulador.simularJornada(temporada, jornada);
		}
	}
}
