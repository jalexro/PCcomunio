package com.ligacomunio.simulacion;

import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.scoring.ScoringService;
import com.ligacomunio.simulacion.SimuladorDePartido.Actuacion;
import com.ligacomunio.simulacion.SimuladorDePartido.PartidoSimulado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Guarda un partido simulado. Va en un bean aparte con transacción por partido, igual que
 * {@code GuardadoImportacion}: así simular una jornada entera no mantiene una única transacción larga.
 *
 * <p>Nunca sobrescribe estadísticas que ya existan. Esa es la regla que permite mezclar jornadas reales
 * (importadas de API-Football) con jornadas simuladas sin perder los datos de verdad.
 */
@Service
public class GuardadoSimulacion {

	private static final Logger log = LoggerFactory.getLogger(GuardadoSimulacion.class);

	private final MatchRepository matchRepository;
	private final PlayerRepository playerRepository;
	private final PlayerMatchStatsRepository statsRepository;
	private final ScoringService scoringService;
	private final SimuladorDePartido simulador;

	public GuardadoSimulacion(MatchRepository matchRepository, PlayerRepository playerRepository,
	                          PlayerMatchStatsRepository statsRepository, ScoringService scoringService,
	                          SimuladorDePartido simulador) {
		this.matchRepository = matchRepository;
		this.playerRepository = playerRepository;
		this.statsRepository = statsRepository;
		this.scoringService = scoringService;
		this.simulador = simulador;
	}

	/**
	 * Simula un partido y devuelve cuántas estadísticas ha escrito, o 0 si no había nada que hacer (ya tenía
	 * estadísticas, o a alguno de los dos equipos no le queda ningún jugador en la base de datos).
	 */
	@Transactional
	public int simularPartido(Long partidoId, long semilla) {
		if (statsRepository.existsByMatchId(partidoId)) {
			return 0;
		}
		Match partido = matchRepository.buscarPorId(partidoId).orElseThrow();
		List<Player> local = playerRepository.findByTeamId(partido.getHomeTeam().getId());
		List<Player> visitante = playerRepository.findByTeamId(partido.getAwayTeam().getId());
		if (local.isEmpty() || visitante.isEmpty()) {
			log.warn("Partido {} sin jugadores en uno de los equipos: no se simula", partidoId);
			return 0;
		}

		PartidoSimulado simulado = simulador.simular(semilla, partidoId, local, visitante,
				partido.getHomeGoals(), partido.getAwayGoals());
		for (Actuacion a : simulado.actuaciones()) {
			statsRepository.save(estadistica(partido, a));
		}
		partido.setHomeGoals(simulado.golesLocal());
		partido.setAwayGoals(simulado.golesVisitante());
		partido.setStatus(MatchStatus.FINALIZADO);
		return simulado.actuaciones().size();
	}

	private PlayerMatchStats estadistica(Match partido, Actuacion a) {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setPlayer(a.jugador());
		s.setMatch(partido);
		s.setMinutes(a.minutos());
		s.setGoals(a.goles());
		s.setAssists(a.asistencias());
		s.setTacklesWon(a.entradas());
		s.setYellowCards(a.amarillas());
		s.setRedCards(a.rojas());
		s.setPassesCompleted(a.pasesCompletados());
		s.setPassesFailed(a.pasesFallados());
		s.setTirosFallados(a.tirosFallados());
		s.setSaves(a.paradas());
		s.setGoalsConceded(a.golesEncajados());
		s.setShotsFaced(a.tirosRecibidos());
		s.setRating(a.rating());
		// Mismas reglas que con los datos reales: los puntos salen del ScoringService, no del simulador.
		s.setPoints(scoringService.calcular(a.jugador().getPosition(), s));
		return s;
	}
}
