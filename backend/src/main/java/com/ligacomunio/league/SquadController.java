package com.ligacomunio.league;

import com.ligacomunio.auth.UsuarioActual;
import com.ligacomunio.league.LeagueDtos.AlineacionRequest;
import com.ligacomunio.league.LeagueDtos.PlantillaDto;
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

@RestController
@RequestMapping("/api/leagues/{leagueId}")
public class SquadController {

	private final SquadService squadService;
	private final AvanceDeJornada avance;

	public SquadController(SquadService squadService, AvanceDeJornada avance) {
		this.squadService = squadService;
		this.avance = avance;
	}

	@GetMapping("/squad")
	public PlantillaDto miPlantilla(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId) {
		return squadService.miPlantilla(UsuarioActual.id(jwt), leagueId);
	}

	@GetMapping("/members/{memberId}/squad")
	public PlantillaDto plantillaDe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long memberId) {
		return squadService.plantillaDe(UsuarioActual.id(jwt), leagueId, memberId);
	}

	@PutMapping("/lineup")
	public PlantillaDto alinear(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId,
	                            @Valid @RequestBody AlineacionRequest request) {
		return squadService.alinear(UsuarioActual.id(jwt), leagueId, request.formation(), request.starterIds());
	}

	/**
	 * Confirma la alineación para la jornada que viene. Si con esto han confirmado todos y la liga juega por
	 * confirmación, la jornada se avanza al momento (fuera de la transacción de la confirmación, para no
	 * mantener bloqueada la liga mientras se simulan los partidos).
	 */
	@PostMapping("/lineup/confirm")
	public PlantillaDto confirmar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId) {
		Long userId = UsuarioActual.id(jwt);
		if (squadService.confirmar(userId, leagueId)) {
			avance.avanzar(leagueId, true);
		}
		return squadService.miPlantilla(userId, leagueId);
	}

	@PostMapping("/squad/{playerId}/sell")
	public PlantillaDto vender(@AuthenticationPrincipal Jwt jwt, @PathVariable Long leagueId, @PathVariable Long playerId) {
		return squadService.vender(UsuarioActual.id(jwt), leagueId, playerId);
	}
}
