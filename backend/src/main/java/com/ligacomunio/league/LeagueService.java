package com.ligacomunio.league;

import com.ligacomunio.league.LeagueDtos.AbonoDto;
import com.ligacomunio.league.LeagueDtos.ClasificacionDto;
import com.ligacomunio.league.LeagueDtos.CrearLigaRequest;
import com.ligacomunio.league.LeagueDtos.LigaDto;
import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.player.Position;
import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LeagueService {

	/** Sin 0/O ni 1/I para que el código se pueda dictar por teléfono. */
	private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int LONGITUD_CODIGO = 8;

	/** Plantilla inicial: 11 jugadores que permiten, como mínimo, un 4-4-2 válido. */
	private static final Map<Position, Integer> PLANTILLA_INICIAL = new LinkedHashMap<>();

	static {
		PLANTILLA_INICIAL.put(Position.PORTERO, 1);
		PLANTILLA_INICIAL.put(Position.DEFENSA, 4);
		PLANTILLA_INICIAL.put(Position.CENTROCAMPISTA, 4);
		PLANTILLA_INICIAL.put(Position.DELANTERO, 2);
	}

	private final AccesoLiga acceso;
	private final LeagueRepository leagueRepository;
	private final LeagueMemberRepository memberRepository;
	private final SquadPlayerRepository squadRepository;
	private final PlayerRepository playerRepository;
	private final MarketListingRepository listingRepository;
	private final MemberMatchdayScoreRepository scoreRepository;
	private final MatchRepository matchRepository;
	private final UserRepository userRepository;
	private final MarketService marketService;
	private final LeagueProperties props;
	private final SecureRandom aleatorio = new SecureRandom();

	public LeagueService(AccesoLiga acceso, LeagueRepository leagueRepository, LeagueMemberRepository memberRepository,
	                     SquadPlayerRepository squadRepository, PlayerRepository playerRepository,
	                     MarketListingRepository listingRepository, MemberMatchdayScoreRepository scoreRepository,
	                     MatchRepository matchRepository, UserRepository userRepository, MarketService marketService,
	                     LeagueProperties props) {
		this.acceso = acceso;
		this.leagueRepository = leagueRepository;
		this.memberRepository = memberRepository;
		this.squadRepository = squadRepository;
		this.playerRepository = playerRepository;
		this.listingRepository = listingRepository;
		this.scoreRepository = scoreRepository;
		this.matchRepository = matchRepository;
		this.userRepository = userRepository;
		this.marketService = marketService;
		this.props = props;
	}

	/** Atajo con los valores de siempre: reparto aleatorio y el presupuesto de application.properties. */
	@Transactional
	public LigaDto crear(Long userId, String nombre) {
		return crear(userId, new CrearLigaRequest(nombre, InitialSquadMode.ALEATORIO, props.initialBudget(), null));
	}

	@Transactional
	public LigaDto crear(Long userId, CrearLigaRequest datos) {
		AppUser dueno = usuario(userId);
		League liga = new League();
		liga.setName(datos.name().trim());
		liga.setOwner(dueno);
		liga.setJoinCode(codigoNuevo());
		liga.setSeason(props.season());
		liga.setInitialSquadMode(datos.initialSquadMode());
		liga.setInitialBudget(datos.initialBudget().setScale(0, RoundingMode.HALF_UP));
		liga.setInitialSquadCap(topeValidado(datos));
		liga.setMatchdayIntervalSeconds(props.defaultIntervalSeconds());
		liga.setNextAdvanceAt(Instant.now().plusSeconds(props.defaultIntervalSeconds()));
		leagueRepository.save(liga);

		marketService.refrescarMercado(liga); // el mercado se crea antes que las plantillas: ningún inicial lo pisa
		LeagueMember miembro = incorporar(liga, dueno);
		return detalle(liga, miembro);
	}

	@Transactional
	public LigaDto unirse(Long userId, String codigo) {
		Long ligaId = leagueRepository.idPorCodigo(codigo.trim().toUpperCase(Locale.ROOT))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código de liga no válido"));
		League liga = acceso.ligaConBloqueo(ligaId);
		if (liga.getStatus() != LeagueStatus.EN_CURSO || liga.getCurrentMatchday() > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga ya ha empezado y no admite más jugadores");
		}
		if (memberRepository.findByLeagueIdAndUserId(liga.getId(), userId).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya estás en esa liga");
		}
		if (memberRepository.countByLeagueId(liga.getId()) >= props.maxMembers()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La liga está completa");
		}
		LeagueMember miembro = incorporar(liga, usuario(userId));
		return detalle(liga, miembro);
	}

	@Transactional(readOnly = true)
	public List<LigaDto> misLigas(Long userId) {
		return leagueRepository.deUsuario(userId).stream()
				.map(l -> detalle(l, acceso.miembro(l.getId(), userId)))
				.toList();
	}

	@Transactional(readOnly = true)
	public LigaDto detalle(Long userId, Long leagueId) {
		return detalle(acceso.liga(leagueId), acceso.miembro(leagueId, userId));
	}

	@Transactional(readOnly = true)
	public List<ClasificacionDto> clasificacion(Long userId, Long leagueId) {
		LeagueMember yo = acceso.miembro(leagueId, userId);
		League liga = acceso.liga(leagueId);
		Map<Long, MemberMatchdayScore> ultima = scoreRepository.deJornada(leagueId, liga.getCurrentMatchday()).stream()
				.collect(Collectors.toMap(s -> s.getMember().getId(), s -> s));
		int proxima = liga.getCurrentMatchday() + 1;

		List<ClasificacionDto> tabla = new ArrayList<>();
		for (LeagueMember m : memberRepository.clasificados(leagueId)) {
			MemberMatchdayScore u = ultima.get(m.getId());
			tabla.add(new ClasificacionDto(tabla.size() + 1, m.getId(), m.getUser().getUsername(), m.getTotalPoints(),
					u == null ? null : u.getPoints(), u == null ? null : u.getEmptySlots(),
					m.getLineupConfirmedMatchday() == proxima, m.getId().equals(yo.getId())));
		}
		return tabla;
	}

	/** Solo el dueño de la liga puede cambiar el ritmo o avanzar la jornada a mano. */
	@Transactional(readOnly = true)
	public void exigirDueno(Long userId, Long leagueId) {
		acceso.miembro(leagueId, userId);
		if (!acceso.liga(leagueId).getOwner().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el dueño de la liga puede hacer esto");
		}
	}

	@Transactional
	public LigaDto cambiarIntervalo(Long userId, Long leagueId, long segundos) {
		League liga = acceso.ligaConBloqueo(leagueId);
		LeagueMember yo = acceso.miembro(leagueId, userId);
		if (!liga.getOwner().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el dueño de la liga puede hacer esto");
		}
		liga.setMatchdayIntervalSeconds(segundos);
		liga.setNextAdvanceAt(Instant.now().plusSeconds(segundos));
		return detalle(liga, yo);
	}

	/**
	 * Solo el dueño: modo de avance, cuánto se abona por punto y el premio por puesto en cada jornada. Rige
	 * desde la próxima jornada. Al pasar a POR_CONFIRMACION el plazo empieza a contar desde ahora, para que
	 * nadie se encuentre con la fecha límite ya vencida.
	 */
	@Transactional
	public LigaDto cambiarOpciones(Long userId, Long leagueId, AdvanceMode modo, BigDecimal valorPunto,
	                               List<BigDecimal> premios) {
		League liga = acceso.ligaConBloqueo(leagueId);
		LeagueMember yo = acceso.miembro(leagueId, userId);
		exigirDuenoDe(liga, userId);
		if (liga.getAdvanceMode() != modo) {
			liga.setAdvanceMode(modo);
			liga.setNextAdvanceAt(Instant.now().plusSeconds(liga.getMatchdayIntervalSeconds()));
		}
		liga.setPointValue(valorPunto.setScale(0, RoundingMode.HALF_UP));
		liga.setPositionBonuses(PremiosPorPosicion.escribir(premios));
		return detalle(liga, yo);
	}

	/**
	 * Solo el dueño: echa a un miembro de la liga. Sus jugadores vuelven a estar libres y se borran sus ofertas y
	 * puntuaciones (la base de datos lo hace en cascada). El dueño no puede expulsarse a sí mismo.
	 */
	@Transactional
	public void expulsar(Long userId, Long leagueId, Long memberId) {
		League liga = acceso.ligaConBloqueo(leagueId);
		acceso.miembro(leagueId, userId);
		exigirDuenoDe(liga, userId);
		LeagueMember expulsado = memberRepository.findByIdAndLeagueId(memberId, leagueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese jugador no está en la liga"));
		if (expulsado.getUser().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes expulsarte de tu propia liga");
		}
		memberRepository.delete(expulsado);
		memberRepository.flush();
	}

	/** Clasificación por abonos: lo cobrado en toda la liga, de más a menos, y el puesto de la última jornada. */
	@Transactional(readOnly = true)
	public List<AbonoDto> abonos(Long userId, Long leagueId) {
		LeagueMember yo = acceso.miembro(leagueId, userId);
		League liga = acceso.liga(leagueId);
		Map<Long, BigDecimal[]> acumulado = new HashMap<>();
		for (Object[] fila : scoreRepository.abonosAcumulados(leagueId)) {
			acumulado.put((Long) fila[0], new BigDecimal[] { (BigDecimal) fila[1], (BigDecimal) fila[2] });
		}
		Map<Long, MemberMatchdayScore> ultima = scoreRepository.deJornada(leagueId, liga.getCurrentMatchday()).stream()
				.collect(Collectors.toMap(s -> s.getMember().getId(), s -> s));

		List<AbonoDto> filas = new ArrayList<>();
		for (LeagueMember m : memberRepository.clasificados(leagueId)) {
			BigDecimal[] total = acumulado.getOrDefault(m.getId(), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			MemberMatchdayScore u = ultima.get(m.getId());
			filas.add(new AbonoDto(0, m.getId(), m.getUser().getUsername(), total[0].add(total[1]), total[0], total[1],
					u == null ? null : u.getPosition(), u == null ? null : u.getPointsPayout().add(u.getPositionBonus()),
					m.getId().equals(yo.getId())));
		}
		filas.sort(Comparator.comparing(AbonoDto::totalPayout).reversed());
		List<AbonoDto> ordenadas = new ArrayList<>();
		for (AbonoDto f : filas) {
			ordenadas.add(new AbonoDto(ordenadas.size() + 1, f.memberId(), f.username(), f.totalPayout(), f.totalFromPoints(),
					f.totalFromPosition(), f.lastMatchdayPosition(), f.lastMatchdayPayout(), f.me()));
		}
		return ordenadas;
	}

	private static void exigirDuenoDe(League liga, Long userId) {
		if (!liga.getOwner().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el dueño de la liga puede hacer esto");
		}
	}

	// --- Auxiliares ---

	/**
	 * El tope solo vale en ALEATORIO_CON_TOPE: allí es obligatorio y en las otras dos modalidades se rechaza
	 * en vez de ignorarlo, para que nadie crea que su liga tiene un tope que no se va a aplicar.
	 */
	private BigDecimal topeValidado(CrearLigaRequest datos) {
		boolean conTope = datos.initialSquadMode() == InitialSquadMode.ALEATORIO_CON_TOPE;
		if (conTope && datos.squadCap() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Hay que fijar el tope de valor de la plantilla inicial");
		}
		if (!conTope && datos.squadCap() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"El tope de valor solo se puede fijar en el reparto aleatorio con tope");
		}
		return conTope ? datos.squadCap().setScale(0, RoundingMode.HALF_UP) : null;
	}

	private LeagueMember incorporar(League liga, AppUser usuario) {
		LeagueMember miembro = new LeagueMember();
		miembro.setLeague(liga);
		miembro.setUser(usuario);
		miembro.setBudget(liga.getInitialBudget());
		memberRepository.save(miembro);
		asignarPlantillaInicial(liga, miembro);
		return miembro;
	}

	/**
	 * Plantilla inicial según la modalidad guardada en la liga: 11 jugadores al azar (1 POR, 4 DEF, 4 MED,
	 * 2 DEL) que nadie tiene aún y que no están en el mercado, con tope de valor o sin él, o ninguno.
	 */
	private void asignarPlantillaInicial(League liga, LeagueMember miembro) {
		if (liga.getInitialSquadMode() == InitialSquadMode.SIN_JUGADORES) {
			return;
		}
		Set<Long> ocupados = new HashSet<>(squadRepository.idsDeJugadores(liga.getId()));
		ocupados.addAll(listingRepository.idsDelCiclo(liga.getId(), liga.getMarketCycle()));
		ocupados.add(-1L); // centinela: la consulta no admite una lista vacía

		Map<Position, List<Player>> candidatos = new EnumMap<>(Position.class);
		PLANTILLA_INICIAL.keySet().forEach(posicion -> candidatos.put(posicion, playerRepository.libres(posicion, ocupados)));

		List<Player> elegidos = liga.getInitialSquadMode() == InitialSquadMode.ALEATORIO_CON_TOPE
				? SorteoDePlantilla.conTope(PLANTILLA_INICIAL, candidatos, liga.getInitialSquadCap(), aleatorio)
				: SorteoDePlantilla.aleatorio(PLANTILLA_INICIAL, candidatos, aleatorio);

		for (Player jugador : elegidos) {
			SquadPlayer fichaje = new SquadPlayer();
			fichaje.setLeague(liga);
			fichaje.setMember(miembro);
			fichaje.setPlayer(jugador);
			fichaje.setStarter(true);
			squadRepository.save(fichaje);
		}
	}

	private LigaDto detalle(League liga, LeagueMember yo) {
		Integer ultima = matchRepository.ultimaJornada(liga.getSeason());
		int proxima = liga.getCurrentMatchday() + 1;
		// La lista de pendientes solo tiene sentido cuando se espera a las confirmaciones.
		List<String> pendientes = liga.getAdvanceMode() == AdvanceMode.POR_CONFIRMACION
				? memberRepository.sinConfirmar(liga.getId(), proxima)
				: List.of();
		return new LigaDto(liga.getId(), liga.getName(), liga.getJoinCode(), liga.getSeason(), liga.getCurrentMatchday(),
				ultima == null ? 0 : ultima, liga.getStatus(), liga.getAdvanceMode(), liga.getMatchdayIntervalSeconds(),
				liga.getNextAdvanceAt(), (int) memberRepository.countByLeagueId(liga.getId()), liga.getOwner().getUsername(),
				liga.getOwner().getId().equals(yo.getUser().getId()), yo.getId(), yo.getBudget(), yo.getFormation(),
				liga.getPointValue(), PremiosPorPosicion.leer(liga.getPositionBonuses()), props.emptySlotPenalty(),
				liga.getInitialSquadMode(), liga.getInitialBudget(), liga.getInitialSquadCap(),
				yo.getLineupConfirmedMatchday() == proxima, pendientes);
	}

	private AppUser usuario(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
	}

	private String codigoNuevo() {
		for (int intento = 0; intento < 20; intento++) {
			StringBuilder codigo = new StringBuilder();
			for (int i = 0; i < LONGITUD_CODIGO; i++) {
				codigo.append(ALFABETO_CODIGO.charAt(aleatorio.nextInt(ALFABETO_CODIGO.length())));
			}
			if (!leagueRepository.existsByJoinCode(codigo.toString())) {
				return codigo.toString();
			}
		}
		throw new IllegalStateException("No se pudo generar un código de liga único");
	}
}
