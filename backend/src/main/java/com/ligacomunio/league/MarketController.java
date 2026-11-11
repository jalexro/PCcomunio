package com.ligacomunio.league;

import com.ligacomunio.auth.UsuarioActual;
import com.ligacomunio.league.LeagueDtos.JugadorMercadoDto;
import com.ligacomunio.league.LeagueDtos.OfertaDirectaRequest;
import com.ligacomunio.league.LeagueDtos.OfertaDto;
import com.ligacomunio.league.LeagueDtos.PujaRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/{leagueId}")
public class MarketController {

	private final MarketService marketService;

	public MarketController(MarketService marketService) {
		this.marketService = marketService;
	}

	@GetMapping("/market")
	public List<JugadorMercadoDto> mercado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId) {
		return marketService.mercado(UsuarioActual.id(jwt), leagueId);
	}

	/** Crea o actualiza mi puja por un jugador del mercado. */
	@PutMapping("/market/{playerId}/bid")
	public OfertaDto pujar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long playerId,
	                       @Valid @RequestBody PujaRequest request) {
		return marketService.pujar(UsuarioActual.id(jwt), leagueId, playerId, request.amount());
	}

	@GetMapping("/offers")
	public List<OfertaDto> ofertas(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId) {
		return marketService.ofertas(UsuarioActual.id(jwt), leagueId);
	}

	/** Oferta directa al usuario que tiene al jugador. */
	@PostMapping("/offers")
	public OfertaDto ofertarDirecta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId,
	                                @Valid @RequestBody OfertaDirectaRequest request) {
		return marketService.ofertarDirecta(UsuarioActual.id(jwt), leagueId, request.playerId(), request.amount());
	}

	@PostMapping("/offers/{offerId}/accept")
	public OfertaDto aceptar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long offerId) {
		return marketService.aceptar(UsuarioActual.id(jwt), leagueId, offerId);
	}

	@PostMapping("/offers/{offerId}/reject")
	public OfertaDto rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long offerId) {
		return marketService.rechazar(UsuarioActual.id(jwt), leagueId, offerId);
	}

	/** Retira mi puja u oferta pendiente. */
	@PostMapping("/offers/{offerId}/cancel")
	public OfertaDto retirar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long offerId) {
		return marketService.retirar(UsuarioActual.id(jwt), leagueId, offerId);
	}
}
