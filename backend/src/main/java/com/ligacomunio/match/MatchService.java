package com.ligacomunio.match;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.scoring.ScoringService;
import com.ligacomunio.team.Team;
import com.ligacomunio.team.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MatchService {

	private final MatchRepository matchRepository;
	private final TeamRepository teamRepository;
	private final PlayerRepository playerRepository;
	private final PlayerMatchStatsRepository statsRepository;
	private final ScoringService scoringService;

	public MatchService(MatchRepository matchRepository, TeamRepository teamRepository,
	                    PlayerRepository playerRepository, PlayerMatchStatsRepository statsRepository,
	                    ScoringService scoringService) {
		this.matchRepository = matchRepository;
		this.teamRepository = teamRepository;
		this.playerRepository = playerRepository;
		this.statsRepository = statsRepository;
		this.scoringService = scoringService;
	}

	@Transactional
	public MatchDto crear(CrearPartidoRequest request) {
		if (request.homeTeamId().equals(request.awayTeamId())) {
			throw peticionInvalida("El equipo local y el visitante deben ser distintos");
		}
		Team local = teamRepository.findById(request.homeTeamId())
				.orElseThrow(() -> peticionInvalida("Equipo local no encontrado"));
		Team visitante = teamRepository.findById(request.awayTeamId())
				.orElseThrow(() -> peticionInvalida("Equipo visitante no encontrado"));

		Match partido = new Match();
		partido.setMatchday(request.matchday());
		partido.setKickoff(request.kickoff());
		partido.setHomeTeam(local);
		partido.setAwayTeam(visitante);
		return MatchDto.from(matchRepository.save(partido));
	}

	@Transactional(readOnly = true)
	public List<MatchDto> listar(Integer jornada) {
		return matchRepository.buscar(jornada).stream().map(MatchDto::from).toList();
	}

	@Transactional(readOnly = true)
	public MatchDto obtener(Long id) {
		return MatchDto.from(partido(id));
	}

	@Transactional
	public MatchDto registrarResultado(Long id, ResultadoRequest request) {
		Match partido = partido(id);
		partido.setHomeGoals(request.homeGoals());
		partido.setAwayGoals(request.awayGoals());
		partido.setStatus(MatchStatus.FINALIZADO);
		return MatchDto.from(partido);
	}

	/** Crea o reemplaza las estadísticas de un jugador en el partido y recalcula sus puntos. */
	@Transactional
	public EstadisticasDto guardarEstadisticas(Long partidoId, EstadisticasRequest request) {
		Match partido = partido(partidoId);
		Player jugador = playerRepository.buscarPorId(request.playerId())
				.orElseThrow(() -> peticionInvalida("Jugador no encontrado"));

		Long equipoId = jugador.getTeam().getId();
		if (!equipoId.equals(partido.getHomeTeam().getId()) && !equipoId.equals(partido.getAwayTeam().getId())) {
			throw peticionInvalida("El jugador no pertenece a ninguno de los equipos del partido");
		}

		PlayerMatchStats stats = statsRepository.findByPlayerIdAndMatchId(jugador.getId(), partido.getId())
				.orElseGet(PlayerMatchStats::new);
		stats.setPlayer(jugador);
		stats.setMatch(partido);
		stats.setMinutes(request.minutes());
		stats.setGoals(request.goals());
		stats.setAssists(request.assists());
		stats.setTacklesWon(request.tacklesWon());
		stats.setYellowCards(request.yellowCards());
		stats.setRedCards(request.redCards());
		stats.setPassesCompleted(request.passesCompleted());
		stats.setPassesFailed(request.passesFailed());
		stats.setTirosFallados(request.tirosFallados());
		stats.setSaves(request.saves());
		stats.setGoalsConceded(request.goalsConceded());
		stats.setShotsFaced(request.shotsFaced());
		stats.setPoints(scoringService.calcular(jugador.getPosition(), stats));

		return EstadisticasDto.from(statsRepository.save(stats));
	}

	@Transactional(readOnly = true)
	public List<EstadisticasDto> estadisticas(Long partidoId) {
		partido(partidoId);
		return statsRepository.buscarPorPartido(partidoId).stream().map(EstadisticasDto::from).toList();
	}

	private Match partido(Long id) {
		return matchRepository.buscarPorId(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partido no encontrado"));
	}

	private ResponseStatusException peticionInvalida(String mensaje) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
	}
}
