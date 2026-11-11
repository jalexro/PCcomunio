package com.ligacomunio.league;

import com.ligacomunio.auth.UsuarioActual;
import com.ligacomunio.league.LeagueDtos.AbonoDto;
import com.ligacomunio.league.LeagueDtos.ClasificacionDto;
import com.ligacomunio.league.LeagueDtos.CrearLigaRequest;
import com.ligacomunio.league.LeagueDtos.IntervaloRequest;
import com.ligacomunio.league.LeagueDtos.LigaDto;
import com.ligacomunio.league.LeagueDtos.OpcionesRequest;
import com.ligacomunio.league.LeagueDtos.UnirseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

	private final LeagueService leagueService;
	private final AvanceDeJornada avance;

	public LeagueController(LeagueService leagueService, AvanceDeJornada avance) {
		this.leagueService = leagueService;
		this.avance = avance;
	}

	@GetMapping
	public List<LigaDto> misLigas(@AuthenticationPrincipal Jwt jwt) {
		return leagueService.misLigas(UsuarioActual.id(jwt));
	}

	/** Crea la liga con el reparto inicial que elija el dueño (modalidad, presupuesto y, si toca, tope). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LigaDto crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearLigaRequest request) {
		return leagueService.crear(UsuarioActual.id(jwt), request);
	}

	@PostMapping("/join")
	public LigaDto unirse(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UnirseRequest request) {
		return leagueService.unirse(UsuarioActual.id(jwt), request.code());
	}

	@GetMapping("/{id}")
	public LigaDto detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return leagueService.detalle(UsuarioActual.id(jwt), id);
	}

	@GetMapping("/{id}/standings")
	public List<ClasificacionDto> clasificacion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return leagueService.clasificacion(UsuarioActual.id(jwt), id);
	}

	/** Solo el dueño: cambia el ritmo de las jornadas (por ejemplo 30 segundos para probar). */
	@PatchMapping("/{id}")
	public LigaDto cambiarIntervalo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
	                                @Valid @RequestBody IntervaloRequest request) {
		return leagueService.cambiarIntervalo(UsuarioActual.id(jwt), id, request.seconds());
	}

	/** Solo el dueño: modo de avance, valor del punto y premios por puesto. */
	@PutMapping("/{id}/options")
	public LigaDto cambiarOpciones(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
	                               @Valid @RequestBody OpcionesRequest request) {
		return leagueService.cambiarOpciones(UsuarioActual.id(jwt), id, request.advanceMode(), request.pointValue(),
				request.positionBonuses());
	}

	/** Solo el dueño: expulsa a un miembro de la liga. */
	@DeleteMapping("/{id}/members/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void expulsar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long memberId) {
		leagueService.expulsar(UsuarioActual.id(jwt), id, memberId);
	}

	@GetMapping("/{id}/payouts")
	public List<AbonoDto> abonos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return leagueService.abonos(UsuarioActual.id(jwt), id);
	}

	/**
	 * Solo el dueño: avanza la jornada ahora mismo. Si la jornada siguiente aún no tiene estadísticas, se
	 * simulan antes; después se usa el mismo código que el reloj.
	 */
	@PostMapping("/{id}/advance")
	public LigaDto avanzar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = UsuarioActual.id(jwt);
		leagueService.exigirDueno(userId, id);
		avance.avanzar(id, false);
		return leagueService.detalle(userId, id);
	}
}
