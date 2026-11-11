package com.ligacomunio.league;

import com.ligacomunio.league.LeagueDtos.JugadorPlantillaDto;
import com.ligacomunio.league.LeagueDtos.PlantillaDto;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.Position;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SquadService {

	private final AccesoLiga acceso;
	private final LeagueMemberRepository memberRepository;
	private final SquadPlayerRepository squadRepository;
	private final PlayerMatchStatsRepository statsRepository;
	private final MarketService marketService;
	private final LeagueProperties props;

	public SquadService(AccesoLiga acceso, LeagueMemberRepository memberRepository, SquadPlayerRepository squadRepository,
	                    PlayerMatchStatsRepository statsRepository, MarketService marketService, LeagueProperties props) {
		this.acceso = acceso;
		this.memberRepository = memberRepository;
		this.squadRepository = squadRepository;
		this.statsRepository = statsRepository;
		this.marketService = marketService;
		this.props = props;
	}

	@Transactional(readOnly = true)
	public PlantillaDto miPlantilla(Long userId, Long leagueId) {
		return plantilla(acceso.liga(leagueId), acceso.miembro(leagueId, userId));
	}

	/** Plantilla de otro miembro de la misma liga (para ver a quién ofertar). */
	@Transactional(readOnly = true)
	public PlantillaDto plantillaDe(Long userId, Long leagueId, Long memberId) {
		acceso.miembro(leagueId, userId);
		LeagueMember rival = memberRepository.findByIdAndLeagueId(memberId, leagueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miembro no encontrado"));
		return plantilla(acceso.liga(leagueId), rival);
	}

	/** Elige la táctica y los 11 titulares: deben ser de mi plantilla y encajar con la táctica. */
	@Transactional
	public PlantillaDto alinear(Long userId, Long leagueId, Formation formacion, List<Long> titularesIds) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		League liga = acceso.liga(leagueId);
		if (liga.getStatus() != LeagueStatus.EN_CURSO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga ha terminado");
		}
		Set<Long> elegidos = new HashSet<>(titularesIds);
		if (elegidos.size() != titularesIds.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay jugadores repetidos en la alineación");
		}
		List<SquadPlayer> plantilla = squadRepository.plantilla(miembro.getId());
		Map<Long, SquadPlayer> porJugador = new HashMap<>();
		plantilla.forEach(s -> porJugador.put(s.getPlayer().getId(), s));
		if (!porJugador.keySet().containsAll(elegidos)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo puedes alinear jugadores de tu plantilla");
		}
		List<Position> posiciones = elegidos.stream().map(id -> porJugador.get(id).getPlayer().getPosition()).toList();
		String motivo = formacion.motivoDeRechazo(posiciones);
		if (motivo != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, motivo);
		}

		plantilla.forEach(s -> s.setStarter(elegidos.contains(s.getPlayer().getId())));
		miembro.setFormation(formacion);
		// Cambiar la alineación deshace la confirmación: lo que se juega es lo último que se ha confirmado.
		miembro.setLineupConfirmedMatchday(0);
		return plantilla(liga, miembro);
	}

	/**
	 * Da por buena la alineación actual para la jornada que viene. En el modo POR_CONFIRMACION, cuando
	 * confirman todos se juega la jornada sin esperar al plazo. Devuelve si con esta confirmación ya están
	 * todos, para que quien llama dispare el avance.
	 */
	@Transactional
	public boolean confirmar(Long userId, Long leagueId) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		League liga = acceso.liga(leagueId);
		if (liga.getStatus() != LeagueStatus.EN_CURSO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga ha terminado");
		}
		int proxima = liga.getCurrentMatchday() + 1;
		miembro.setLineupConfirmedMatchday(proxima);
		// Flush explícito: el recuento es una consulta y tiene que ver también esta confirmación. Hibernate
		// lo haría solo antes de la consulta, pero dejarlo escrito evita tener que adivinarlo al leer esto.
		memberRepository.flush();
		long miembros = memberRepository.countByLeagueId(leagueId);
		return memberRepository.contarConfirmados(leagueId, proxima) == miembros;
	}

	/** Vende un jugador al mercado al instante por su valor: libera hueco y suma el dinero al presupuesto. */
	@Transactional
	public PlantillaDto vender(Long userId, Long leagueId, Long playerId) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		League liga = acceso.liga(leagueId);
		if (liga.getStatus() != LeagueStatus.EN_CURSO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga ha terminado");
		}
		SquadPlayer fichaje = squadRepository.dueno(leagueId, playerId)
				.filter(s -> s.getMember().getId().equals(miembro.getId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese jugador no está en tu plantilla"));
		BigDecimal valor = fichaje.getPlayer().getMarketValue();

		miembro.setBudget(miembro.getBudget().add(valor));
		squadRepository.delete(fichaje);
		squadRepository.flush();
		marketService.anularPendientes(leagueId, playerId, null);
		return plantilla(liga, miembro);
	}

	private PlantillaDto plantilla(League liga, LeagueMember miembro) {
		Map<Long, BigDecimal> puntosUltimaJornada = new HashMap<>();
		if (liga.getCurrentMatchday() > 0) {
			for (Object[] fila : statsRepository.puntosDeLaJornada(liga.getSeason(), liga.getCurrentMatchday())) {
				puntosUltimaJornada.merge((Long) fila[0], fila[1] == null ? BigDecimal.ZERO : (BigDecimal) fila[1], BigDecimal::add);
			}
		}
		List<JugadorPlantillaDto> jugadores = squadRepository.plantilla(miembro.getId()).stream()
				.map(s -> {
					Player p = s.getPlayer();
					return new JugadorPlantillaDto(p.getId(), p.getName(), p.getPosition(), p.getTeam().getName(),
							p.getTeam().getShortName(), p.getMarketValue(), s.isStarter(), s.getPricePaid(),
							puntosUltimaJornada.get(p.getId()), p.getStatus());
				})
				.sorted(Comparator.comparing((JugadorPlantillaDto j) -> j.position().ordinal()).thenComparing(JugadorPlantillaDto::name))
				.toList();
		return new PlantillaDto(miembro.getId(), miembro.getUser().getUsername(), miembro.getFormation(), miembro.getBudget(),
				props.maxSquadSize(), jugadores);
	}
}
