package com.ligacomunio.league;

import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avance de jornada de una liga. Orden: resolver el mercado, puntuar las alineaciones con los puntos reales de
 * la jornada, refrescar el mercado y programar la siguiente. El mismo código lo ejecutan el reloj y el botón
 * manual del dueño (útil para probar).
 */
@Service
public class MatchdayService {

	private static final Logger log = LoggerFactory.getLogger(MatchdayService.class);

	private final AccesoLiga acceso;
	private final LeagueMemberRepository memberRepository;
	private final SquadPlayerRepository squadRepository;
	private final MemberMatchdayScoreRepository scoreRepository;
	private final MatchRepository matchRepository;
	private final PlayerMatchStatsRepository statsRepository;
	private final MarketService marketService;
	private final LeagueProperties props;

	public MatchdayService(AccesoLiga acceso, LeagueMemberRepository memberRepository, SquadPlayerRepository squadRepository,
	                       MemberMatchdayScoreRepository scoreRepository, MatchRepository matchRepository,
	                       PlayerMatchStatsRepository statsRepository, MarketService marketService,
	                       LeagueProperties props) {
		this.props = props;
		this.acceso = acceso;
		this.memberRepository = memberRepository;
		this.squadRepository = squadRepository;
		this.scoreRepository = scoreRepository;
		this.matchRepository = matchRepository;
		this.statsRepository = statsRepository;
		this.marketService = marketService;
	}

	/**
	 * Avanza una jornada. Con soloSiVencida (lo usan el reloj y la confirmación del último miembro) no hace nada
	 * si todavía no toca; sin ella (botón del dueño) avanza siempre. Si la jornada siguiente aún no tiene todos
	 * sus partidos y estadísticas, lanza un 409 sin cambiar nada.
	 */
	@Transactional
	public void avanzar(Long leagueId, boolean soloSiVencida) {
		League liga = acceso.ligaConBloqueo(leagueId);
		if (liga.getStatus() != LeagueStatus.EN_CURSO) {
			if (soloSiVencida) return;
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga ha terminado");
		}
		if (soloSiVencida && !tocaAvanzar(liga)) {
			return;
		}

		int jornada = liga.getCurrentMatchday() + 1;
		Integer ultima = matchRepository.ultimaJornada(liga.getSeason());
		int ultimaJornada = ultima == null ? 0 : ultima;
		if (jornada > ultimaJornada) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "No quedan más jornadas en la temporada " + liga.getSeason());
		}
		if (!jornadaLista(liga.getSeason(), jornada)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La jornada " + jornada
					+ " aún no está disponible: faltan partidos por jugar o estadísticas por importar");
		}

		marketService.resolverMercado(liga);
		puntuar(liga, jornada);
		liga.setCurrentMatchday(jornada);
		if (jornada >= ultimaJornada) {
			liga.setStatus(LeagueStatus.FINALIZADA);
			log.info("Liga {} finalizada tras la jornada {}", liga.getId(), jornada);
		} else {
			marketService.refrescarMercado(liga);
			liga.setNextAdvanceAt(Instant.now().plusSeconds(liga.getMatchdayIntervalSeconds()));
		}
		log.info("Liga {} avanzada a la jornada {}", liga.getId(), jornada);
	}

	/**
	 * Si la jornada siguiente ya se puede jugar sin que nadie pulse nada. Por tiempo, cuando vence el reloj;
	 * por confirmación, en cuanto han confirmado todos los miembros o cuando se pasa la fecha límite.
	 */
	public boolean tocaAvanzar(League liga) {
		if (liga.getAdvanceMode() == AdvanceMode.POR_CONFIRMACION && todosConfirmados(liga)) {
			return true;
		}
		return !liga.getNextAdvanceAt().isAfter(Instant.now());
	}

	/** Cierto si todos los miembros han confirmado ya su alineación para la jornada que viene. */
	public boolean todosConfirmados(League liga) {
		long miembros = memberRepository.countByLeagueId(liga.getId());
		return miembros > 0
				&& memberRepository.contarConfirmados(liga.getId(), liga.getCurrentMatchday() + 1) == miembros;
	}

	/** Todos los partidos de la jornada finalizados y con estadísticas (importadas o simuladas). */
	public boolean jornadaLista(int temporada, int jornada) {
		return matchRepository.countBySeasonAndMatchday(temporada, jornada) > 0
				&& matchRepository.countBySeasonAndMatchdayAndStatusNot(temporada, jornada, MatchStatus.FINALIZADO) == 0
				&& matchRepository.pendientesDeEstadisticas(temporada, jornada, MatchStatus.FINALIZADO).isEmpty();
	}

	/**
	 * Suma, por miembro, los puntos de la jornada de sus titulares (quien no jugó o no está titular suma 0),
	 * descuenta la penalización por los puestos de la táctica que se hayan quedado sin jugador y abona al
	 * presupuesto: puntos x valor del punto, más el premio del puesto que ocupa en la jornada. Los empates
	 * comparten puesto y premio. Quien no suma puntos positivos no cobra nada.
	 */
	private void puntuar(League liga, int jornada) {
		Map<Long, BigDecimal> puntosJugador = new HashMap<>();
		for (Object[] fila : statsRepository.puntosDeLaJornada(liga.getSeason(), jornada)) {
			puntosJugador.merge((Long) fila[0], fila[1] == null ? BigDecimal.ZERO : (BigDecimal) fila[1], BigDecimal::add);
		}
		Map<Long, BigDecimal> puntosMiembro = new HashMap<>();
		Map<Long, Integer> titularesMiembro = new HashMap<>();
		for (SquadPlayer titular : squadRepository.titularesDeLiga(liga.getId())) {
			puntosMiembro.merge(titular.getMember().getId(),
					puntosJugador.getOrDefault(titular.getPlayer().getId(), BigDecimal.ZERO), BigDecimal::add);
			titularesMiembro.merge(titular.getMember().getId(), 1, Integer::sum);
		}

		List<LeagueMember> miembros = memberRepository.clasificados(liga.getId());
		Map<Long, BigDecimal> puntos = new HashMap<>();
		Map<Long, Integer> huecos = new HashMap<>();
		Map<Long, BigDecimal> castigos = new HashMap<>();
		for (LeagueMember m : miembros) {
			// Un puesto vacío es un titular que ya no está en la plantilla (vendido o traspasado) o que nunca se
			// cubrió: la alineación se juega igual, pero cada hueco cuesta puntos.
			int vacios = Math.max(0, Formation.TITULARES - titularesMiembro.getOrDefault(m.getId(), 0));
			BigDecimal castigo = props.emptySlotPenalty().multiply(BigDecimal.valueOf(vacios));
			huecos.put(m.getId(), vacios);
			castigos.put(m.getId(), castigo);
			puntos.put(m.getId(), puntosMiembro.getOrDefault(m.getId(), BigDecimal.ZERO)
					.subtract(castigo).setScale(2, RoundingMode.HALF_UP));
		}
		List<LeagueMember> orden = new ArrayList<>(miembros);
		orden.sort(Comparator.comparing((LeagueMember m) -> puntos.get(m.getId())).reversed());

		List<BigDecimal> premios = PremiosPorPosicion.leer(liga.getPositionBonuses());
		int puesto = 0;
		BigDecimal anterior = null;
		for (int i = 0; i < orden.size(); i++) {
			LeagueMember miembro = orden.get(i);
			BigDecimal p = puntos.get(miembro.getId());
			if (anterior == null || p.compareTo(anterior) != 0) {
				puesto = i + 1;
				anterior = p;
			}
			boolean cobra = p.signum() > 0;
			BigDecimal abonoPuntos = cobra ? p.multiply(liga.getPointValue()).setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
			BigDecimal premio = cobra ? PremiosPorPosicion.premio(premios, puesto) : BigDecimal.ZERO;

			MemberMatchdayScore marca = new MemberMatchdayScore();
			marca.setMember(miembro);
			marca.setMatchday(jornada);
			marca.setPoints(p);
			marca.setPosition(puesto);
			marca.setPointsPayout(abonoPuntos);
			marca.setPositionBonus(premio);
			marca.setEmptySlots(huecos.getOrDefault(miembro.getId(), 0));
			marca.setEmptySlotPenalty(castigos.getOrDefault(miembro.getId(), BigDecimal.ZERO));
			scoreRepository.save(marca);
			miembro.setTotalPoints(miembro.getTotalPoints().add(p));
			miembro.setBudget(miembro.getBudget().add(abonoPuntos).add(premio));
		}
	}
}
