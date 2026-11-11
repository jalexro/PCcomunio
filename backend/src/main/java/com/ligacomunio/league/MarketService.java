package com.ligacomunio.league;

import com.ligacomunio.league.LeagueDtos.JugadorMercadoDto;
import com.ligacomunio.league.LeagueDtos.OfertaDto;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mercado de fichajes: pujas al mercado (se resuelven al avanzar la jornada) y ofertas directas al dueño de
 * un jugador (las acepta o rechaza el dueño). Toda escritura se hace con la liga bloqueada.
 */
@Service
public class MarketService {

	private static final int OFERTAS_MOSTRADAS = 50;

	private final AccesoLiga acceso;
	private final SquadPlayerRepository squadRepository;
	private final PlayerRepository playerRepository;
	private final MarketListingRepository listingRepository;
	private final OfferRepository offerRepository;
	private final LeagueProperties props;

	public MarketService(AccesoLiga acceso, SquadPlayerRepository squadRepository, PlayerRepository playerRepository,
	                     MarketListingRepository listingRepository, OfferRepository offerRepository, LeagueProperties props) {
		this.acceso = acceso;
		this.squadRepository = squadRepository;
		this.playerRepository = playerRepository;
		this.listingRepository = listingRepository;
		this.offerRepository = offerRepository;
		this.props = props;
	}

	// --- Consultas ---

	@Transactional(readOnly = true)
	public List<JugadorMercadoDto> mercado(Long userId, Long leagueId) {
		LeagueMember miembro = acceso.miembro(leagueId, userId);
		League liga = acceso.liga(leagueId);
		Map<Long, BigDecimal> misPujas = offerRepository.pujasDelMiembro(miembro.getId(), OfferStatus.PENDIENTE).stream()
				.collect(Collectors.toMap(o -> o.getPlayer().getId(), Offer::getAmount, (a, b) -> b));

		return listingRepository.delCiclo(leagueId, liga.getMarketCycle()).stream()
				.map(MarketListing::getPlayer)
				.map(p -> new JugadorMercadoDto(p.getId(), p.getName(), p.getPosition(), p.getTeam().getName(),
						p.getTeam().getShortName(), p.getMarketValue(), MarketRules.minimo(p.getMarketValue()),
						MarketRules.maximo(p.getMarketValue(), props.bidCapPercent()), misPujas.get(p.getId()), p.getStatus()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OfertaDto> ofertas(Long userId, Long leagueId) {
		LeagueMember miembro = acceso.miembro(leagueId, userId);
		return offerRepository.delMiembro(miembro.getId(), PageRequest.of(0, OFERTAS_MOSTRADAS)).stream()
				.map(o -> aDto(o, miembro.getId()))
				.toList();
	}

	// --- Acciones del usuario ---

	/** Puja (o actualiza la puja) por un jugador del mercado. */
	@Transactional
	public OfertaDto pujar(Long userId, Long leagueId, Long playerId, BigDecimal importe) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		League liga = acceso.liga(leagueId);
		exigirEnCurso(liga);
		if (!listingRepository.existsByLeagueIdAndPlayerIdAndCycle(leagueId, playerId, liga.getMarketCycle())) {
			throw conflicto(HttpStatus.BAD_REQUEST, "Ese jugador no está en el mercado");
		}
		if (squadRepository.existsByLeagueIdAndPlayerId(leagueId, playerId)) {
			throw conflicto(HttpStatus.CONFLICT, "Ese jugador ya tiene dueño");
		}
		Player jugador = jugador(playerId);
		validarOferta(miembro, importe, jugador);
		Offer oferta = ofertaPendiente(miembro, jugador, liga, null, importe);
		return aDto(offerRepository.save(oferta), miembro.getId());
	}

	/** Oferta directa al usuario que tiene al jugador. */
	@Transactional
	public OfertaDto ofertarDirecta(Long userId, Long leagueId, Long playerId, BigDecimal importe) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		League liga = acceso.liga(leagueId);
		exigirEnCurso(liga);
		SquadPlayer dueno = squadRepository.dueno(leagueId, playerId).orElseThrow(() -> conflicto(HttpStatus.NOT_FOUND,
				"Ese jugador no pertenece a ningún usuario: si aparece en el mercado, puja por él allí"));
		if (dueno.getMember().getId().equals(miembro.getId())) {
			throw conflicto(HttpStatus.BAD_REQUEST, "No puedes ofertar por tu propio jugador");
		}
		Player jugador = jugador(playerId);
		validarOferta(miembro, importe, jugador);
		Offer oferta = ofertaPendiente(miembro, jugador, liga, dueno.getMember(), importe);
		return aDto(offerRepository.save(oferta), miembro.getId());
	}

	/** El comprador retira su puja u oferta pendiente. */
	@Transactional
	public OfertaDto retirar(Long userId, Long leagueId, Long offerId) {
		LeagueMember miembro = acceso.miembroConBloqueo(leagueId, userId);
		Offer oferta = ofertaDeLiga(leagueId, offerId);
		if (!oferta.getBidder().getId().equals(miembro.getId())) {
			throw conflicto(HttpStatus.NOT_FOUND, "Oferta no encontrada");
		}
		exigirPendiente(oferta);
		oferta.resolver(OfferStatus.RETIRADA);
		return aDto(oferta, miembro.getId());
	}

	/**
	 * El dueño acepta una oferta directa. El traspaso es inmediato. Si ya no se puede cumplir (el dueño ya no
	 * tiene al jugador, o el comprador se ha quedado sin fondos o sin hueco) la oferta se anula; por eso se
	 * confirma la transacción aunque se devuelva un error.
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public OfertaDto aceptar(Long userId, Long leagueId, Long offerId) {
		LeagueMember dueno = acceso.miembroConBloqueo(leagueId, userId);
		Offer oferta = ofertaDeLiga(leagueId, offerId);
		if (oferta.getSeller() == null || !oferta.getSeller().getId().equals(dueno.getId())) {
			throw conflicto(HttpStatus.NOT_FOUND, "Oferta no encontrada");
		}
		exigirPendiente(oferta);
		exigirEnCurso(acceso.liga(leagueId));

		SquadPlayer fichaje = squadRepository.dueno(leagueId, oferta.getPlayer().getId()).orElse(null);
		if (fichaje == null || !fichaje.getMember().getId().equals(dueno.getId())) {
			oferta.resolver(OfferStatus.ANULADA);
			throw conflicto(HttpStatus.CONFLICT, "Ya no tienes a ese jugador");
		}
		LeagueMember comprador = oferta.getBidder();
		if (comprador.getBudget().compareTo(oferta.getAmount()) < 0) {
			oferta.resolver(OfferStatus.ANULADA);
			throw conflicto(HttpStatus.CONFLICT, "El comprador ya no tiene presupuesto suficiente");
		}
		if (squadRepository.countByMemberId(comprador.getId()) >= props.maxSquadSize()) {
			oferta.resolver(OfferStatus.ANULADA);
			throw conflicto(HttpStatus.CONFLICT, "La plantilla del comprador está completa");
		}

		comprador.setBudget(comprador.getBudget().subtract(oferta.getAmount()));
		dueno.setBudget(dueno.getBudget().add(oferta.getAmount()));
		fichaje.setMember(comprador);
		fichaje.setStarter(false);
		fichaje.setPricePaid(oferta.getAmount());
		fichaje.setAcquiredAt(Instant.now());
		oferta.resolver(OfferStatus.ACEPTADA);
		anularPendientes(leagueId, oferta.getPlayer().getId(), oferta);
		return aDto(oferta, dueno.getId());
	}

	@Transactional
	public OfertaDto rechazar(Long userId, Long leagueId, Long offerId) {
		LeagueMember dueno = acceso.miembroConBloqueo(leagueId, userId);
		Offer oferta = ofertaDeLiga(leagueId, offerId);
		if (oferta.getSeller() == null || !oferta.getSeller().getId().equals(dueno.getId())) {
			throw conflicto(HttpStatus.NOT_FOUND, "Oferta no encontrada");
		}
		exigirPendiente(oferta);
		oferta.resolver(OfferStatus.RECHAZADA);
		return aDto(oferta, dueno.getId());
	}

	// --- Operaciones internas (se llaman con la liga ya bloqueada) ---

	/** Anula las ofertas pendientes sobre un jugador cuyo dueño ha cambiado (salvo la indicada). */
	void anularPendientes(Long leagueId, Long playerId, Offer excepto) {
		for (Offer pendiente : offerRepository.delJugador(leagueId, playerId, OfferStatus.PENDIENTE)) {
			if (excepto == null || !pendiente.getId().equals(excepto.getId())) {
				pendiente.resolver(OfferStatus.ANULADA);
			}
		}
	}

	/**
	 * Resuelve las pujas al mercado: para cada jugador gana la puja válida más alta (a igual importe, la primera).
	 * Una puja se anula si el comprador ya no tiene fondos o hueco; entonces opta la siguiente. Las ofertas
	 * directas sin responder caducan.
	 */
	void resolverMercado(League liga) {
		Set<Long> adjudicados = new HashSet<>();
		for (Offer puja : offerRepository.pujasDeMercado(liga.getId(), OfferStatus.PENDIENTE)) {
			Long playerId = puja.getPlayer().getId();
			LeagueMember comprador = puja.getBidder();
			if (adjudicados.contains(playerId)) {
				puja.resolver(OfferStatus.PERDIDA);
			} else if (squadRepository.existsByLeagueIdAndPlayerId(liga.getId(), playerId)
					|| comprador.getBudget().compareTo(puja.getAmount()) < 0
					|| squadRepository.countByMemberId(comprador.getId()) >= props.maxSquadSize()) {
				puja.resolver(OfferStatus.ANULADA);
			} else {
				SquadPlayer fichaje = new SquadPlayer();
				fichaje.setLeague(liga);
				fichaje.setMember(comprador);
				fichaje.setPlayer(puja.getPlayer());
				fichaje.setPricePaid(puja.getAmount());
				squadRepository.save(fichaje);
				comprador.setBudget(comprador.getBudget().subtract(puja.getAmount()));
				puja.resolver(OfferStatus.ACEPTADA);
				adjudicados.add(playerId);
			}
		}
		for (Offer directa : offerRepository.directasDeLiga(liga.getId(), OfferStatus.PENDIENTE)) {
			directa.resolver(OfferStatus.CADUCADA);
		}
	}

	/** Sustituye el mercado por un conjunto nuevo de jugadores libres elegidos al azar. */
	void refrescarMercado(League liga) {
		listingRepository.borrarDeLiga(liga.getId());
		liga.setMarketCycle(liga.getMarketCycle() + 1);

		Set<Long> ocupados = new HashSet<>(squadRepository.idsDeJugadores(liga.getId()));
		ocupados.add(-1L); // centinela: la consulta no admite una lista vacía
		List<Player> libres = new ArrayList<>(playerRepository.libresDeCualquierPosicion(ocupados));
		Collections.shuffle(libres);
		libres.stream().limit(props.marketSize()).forEach(jugador -> {
			MarketListing anuncio = new MarketListing();
			anuncio.setLeague(liga);
			anuncio.setPlayer(jugador);
			anuncio.setCycle(liga.getMarketCycle());
			listingRepository.save(anuncio);
		});
	}

	// --- Auxiliares ---

	private Offer ofertaPendiente(LeagueMember comprador, Player jugador, League liga, LeagueMember vendedor, BigDecimal importe) {
		Offer oferta = offerRepository.delPostor(comprador.getId(), jugador.getId(), OfferStatus.PENDIENTE).orElseGet(() -> {
			Offer nueva = new Offer();
			nueva.setLeague(liga);
			nueva.setPlayer(jugador);
			nueva.setBidder(comprador);
			nueva.setSeller(vendedor);
			return nueva;
		});
		oferta.setAmount(importe.setScale(2));
		oferta.setCreatedAt(Instant.now());
		return oferta;
	}

	private void validarOferta(LeagueMember comprador, BigDecimal importe, Player jugador) {
		String motivo = MarketRules.motivoDeRechazo(importe, jugador.getMarketValue(), props.bidCapPercent());
		if (motivo != null) {
			throw conflicto(HttpStatus.BAD_REQUEST, motivo);
		}
		if (comprador.getBudget().compareTo(importe) < 0) {
			throw conflicto(HttpStatus.CONFLICT, "Presupuesto insuficiente");
		}
		if (squadRepository.countByMemberId(comprador.getId()) >= props.maxSquadSize()) {
			throw conflicto(HttpStatus.CONFLICT, "Tu plantilla está completa (máximo " + props.maxSquadSize() + " jugadores)");
		}
	}

	private Player jugador(Long playerId) {
		return playerRepository.buscarPorId(playerId)
				.orElseThrow(() -> conflicto(HttpStatus.NOT_FOUND, "Jugador no encontrado"));
	}

	private Offer ofertaDeLiga(Long leagueId, Long offerId) {
		return offerRepository.findByIdAndLeagueId(offerId, leagueId)
				.orElseThrow(() -> conflicto(HttpStatus.NOT_FOUND, "Oferta no encontrada"));
	}

	private void exigirPendiente(Offer oferta) {
		if (oferta.getStatus() != OfferStatus.PENDIENTE) {
			throw conflicto(HttpStatus.CONFLICT, "La oferta ya no está pendiente");
		}
	}

	private void exigirEnCurso(League liga) {
		if (liga.getStatus() != LeagueStatus.EN_CURSO) {
			throw conflicto(HttpStatus.CONFLICT, "La liga ha terminado");
		}
	}

	private OfertaDto aDto(Offer o, Long miMiembroId) {
		Player p = o.getPlayer();
		return new OfertaDto(o.getId(), p.getId(), p.getName(), p.getPosition(), p.getTeam().getShortName(),
				p.getMarketValue(), o.getBidder().getUser().getUsername(),
				o.getSeller() == null ? null : o.getSeller().getUser().getUsername(),
				o.getAmount(), o.getStatus(), o.getCreatedAt(),
				o.getBidder().getId().equals(miMiembroId),
				o.getSeller() != null && o.getSeller().getId().equals(miMiembroId));
	}

	private ResponseStatusException conflicto(HttpStatus estado, String mensaje) {
		return new ResponseStatusException(estado, mensaje);
	}
}
