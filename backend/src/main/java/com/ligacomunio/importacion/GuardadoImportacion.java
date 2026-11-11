package com.ligacomunio.importacion;

import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.scoring.ScoringService;
import com.ligacomunio.team.Team;
import com.ligacomunio.team.TeamRepository;
import com.ligacomunio.valores.GeneradorDeValores;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistencia de lo importado. Va en un bean aparte, con transacciones cortas, para no mantener
 * una conexión a la base de datos abierta mientras se espera a la API externa.
 */
@Service
public class GuardadoImportacion {

	/** Partido finalizado pendiente de importar estadísticas. */
	public record PartidoPendiente(Long id, Long externalId) {
	}

	/** Cuántas filas se guardaron y cuántas se descartaron (jugadores sin minutos o sin equipo conocido). */
	public record Guardado(int guardados, int omitidos) {
	}

	private final TeamRepository teamRepository;
	private final PlayerRepository playerRepository;
	private final MatchRepository matchRepository;
	private final PlayerMatchStatsRepository statsRepository;
	private final ScoringService scoringService;
	private final GeneradorDeValores generadorDeValores;

	public GuardadoImportacion(TeamRepository teamRepository, PlayerRepository playerRepository,
	                           MatchRepository matchRepository, PlayerMatchStatsRepository statsRepository,
	                           ScoringService scoringService, GeneradorDeValores generadorDeValores) {
		this.teamRepository = teamRepository;
		this.playerRepository = playerRepository;
		this.matchRepository = matchRepository;
		this.statsRepository = statsRepository;
		this.scoringService = scoringService;
		this.generadorDeValores = generadorDeValores;
	}

	@Transactional
	public int guardarEquipos(List<EquipoExterno> equipos) {
		for (EquipoExterno e : equipos) {
			Team equipo = teamRepository.findByExternalId(e.id()).orElseGet(Team::new);
			equipo.setExternalId(e.id());
			equipo.setName(e.nombre());
			equipo.setShortName(e.codigo() != null ? e.codigo() : abreviar(e.nombre()));
			equipo.setCrestUrl(e.escudo());
			teamRepository.save(equipo);
		}
		return equipos.size();
	}

	@Transactional
	public Guardado guardarPartidos(int temporada, List<PartidoExterno> partidos) {
		Map<Long, Team> equipos = new HashMap<>();
		teamRepository.findAll().stream().filter(t -> t.getExternalId() != null)
				.forEach(t -> equipos.put(t.getExternalId(), t));

		int guardados = 0;
		int omitidos = 0;
		for (PartidoExterno p : partidos) {
			Team local = equipos.get(p.localId());
			Team visitante = equipos.get(p.visitanteId());
			if (p.jornada() < 1 || local == null || visitante == null) {
				omitidos++;
				continue;
			}
			Match partido = matchRepository.findByExternalId(p.id()).orElseGet(Match::new);
			partido.setExternalId(p.id());
			partido.setSeason(temporada);
			partido.setMatchday(p.jornada());
			partido.setKickoff(p.fecha());
			partido.setHomeTeam(local);
			partido.setAwayTeam(visitante);
			partido.setHomeGoals(p.golesLocal());
			partido.setAwayGoals(p.golesVisitante());
			partido.setStatus(p.estado());
			matchRepository.save(partido);
			guardados++;
		}
		return new Guardado(guardados, omitidos);
	}

	/**
	 * Crea o actualiza los jugadores de las plantillas. Requiere tener ya los equipos: los jugadores de un
	 * equipo desconocido se omiten. No toca el valor de mercado, que lo ponen después los valores externos
	 * y {@link #generarValoresQueFaltan()}.
	 */
	@Transactional
	public Guardado guardarPlantillas(List<JugadorExterno> jugadores) {
		Map<Long, Team> equipos = new HashMap<>();
		teamRepository.findAll().stream().filter(t -> t.getExternalId() != null)
				.forEach(t -> equipos.put(t.getExternalId(), t));

		int guardados = 0;
		int omitidos = 0;
		for (JugadorExterno j : jugadores) {
			Team equipo = equipos.get(j.equipoId());
			if (equipo == null) {
				omitidos++;
				continue;
			}
			Player jugador = playerRepository.findByExternalId(j.id()).orElseGet(Player::new);
			jugador.setExternalId(j.id());
			jugador.setName(j.nombre());
			jugador.setPosition(j.posicion());
			jugador.setTeam(equipo);
			if (j.foto() != null) {
				jugador.setPhotoUrl(j.foto());
			}
			playerRepository.save(jugador);
			guardados++;
		}
		return new Guardado(guardados, omitidos);
	}

	/**
	 * Pone un valor generado a los jugadores que han quedado sin precio real. Sin valor, un jugador no se
	 * puede fichar ni repartir, así que se quedaría fuera del juego.
	 */
	@Transactional
	public int generarValoresQueFaltan() {
		List<Player> sinValor = playerRepository.sinValor();
		sinValor.forEach(p -> p.setMarketValue(generadorDeValores.valorPara(p)));
		return sinValor.size();
	}

	@Transactional(readOnly = true)
	public List<PartidoPendiente> partidosPendientes(int temporada, int jornada) {
		return matchRepository.pendientesDeEstadisticas(temporada, jornada, MatchStatus.FINALIZADO).stream()
				.map(m -> new PartidoPendiente(m.getId(), m.getExternalId()))
				.toList();
	}

	/** Crea los jugadores que falten, guarda sus estadísticas y calcula los puntos con ScoringService. */
	@Transactional
	public Guardado guardarEstadisticas(Long partidoId, List<EstadisticaExterna> estadisticas) {
		Match partido = matchRepository.findById(partidoId).orElseThrow();
		int guardados = 0;
		int omitidos = 0;
		for (EstadisticaExterna e : estadisticas) {
			if (e.minutos() <= 0) {
				omitidos++; // suplente que no jugó
				continue;
			}
			Player jugador = playerRepository.findByExternalId(e.jugadorId()).orElse(null);
			if (jugador == null) {
				Team equipo = teamRepository.findByExternalId(e.equipoId()).orElse(null);
				if (equipo == null) {
					omitidos++;
					continue;
				}
				jugador = new Player();
				jugador.setExternalId(e.jugadorId());
				jugador.setName(e.nombre());
				jugador.setPosition(e.posicion());
				jugador.setTeam(equipo);
				jugador.setPhotoUrl(e.foto());
				jugador = playerRepository.save(jugador);
			}

			PlayerMatchStats stats = statsRepository.findByPlayerIdAndMatchId(jugador.getId(), partido.getId())
					.orElseGet(PlayerMatchStats::new);
			stats.setPlayer(jugador);
			stats.setMatch(partido);
			stats.setMinutes(e.minutos());
			stats.setGoals(e.goles());
			stats.setAssists(e.asistencias());
			stats.setTacklesWon(e.entradas());
			stats.setYellowCards(e.amarillas());
			stats.setRedCards(e.rojas());
			stats.setPassesCompleted(e.pasesCompletados());
			stats.setPassesFailed(e.pasesFallados());
			stats.setTirosFallados(e.tirosFallados());
			stats.setSaves(e.paradas());
			stats.setGoalsConceded(e.golesEncajados());
			stats.setShotsFaced(e.tirosRecibidos());
			stats.setRating(e.rating());
			// Los puntos dependen de la posición registrada del jugador, no de la que traiga este partido.
			stats.setPoints(scoringService.calcular(jugador.getPosition(), stats));
			statsRepository.save(stats);
			guardados++;
		}
		return new Guardado(guardados, omitidos);
	}

	private String abreviar(String nombre) {
		return nombre.substring(0, Math.min(3, nombre.length())).toUpperCase();
	}
}
