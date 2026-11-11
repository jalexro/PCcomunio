package com.ligacomunio.league;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Comprobaciones de acceso comunes: una liga solo existe para sus miembros. */
@Component
public class AccesoLiga {

	private final LeagueRepository leagueRepository;
	private final LeagueMemberRepository memberRepository;

	public AccesoLiga(LeagueRepository leagueRepository, LeagueMemberRepository memberRepository) {
		this.leagueRepository = leagueRepository;
		this.memberRepository = memberRepository;
	}

	/** Miembro de la liga o 404: a quien no es miembro no se le revela que la liga existe. */
	public LeagueMember miembro(Long leagueId, Long userId) {
		return memberRepository.findByLeagueIdAndUserId(leagueId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liga no encontrada"));
	}

	/**
	 * Igual que {@link #miembro} pero bloqueando antes la liga para escribir con seguridad. El orden importa:
	 * el miembro se lee DESPUÉS de obtener el bloqueo, para no trabajar con un presupuesto ya desactualizado.
	 */
	public LeagueMember miembroConBloqueo(Long leagueId, Long userId) {
		ligaConBloqueo(leagueId);
		return miembro(leagueId, userId);
	}

	public League liga(Long leagueId) {
		return leagueRepository.findById(leagueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liga no encontrada"));
	}

	public League ligaConBloqueo(Long leagueId) {
		return leagueRepository.buscarConBloqueo(leagueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liga no encontrada"));
	}
}
