package com.ligacomunio.league;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Reloj del juego: cada cierto tiempo avanza las ligas a las que les toca. Por tiempo, cuando vence el
 * plazo; por confirmación, en cuanto todos los miembros han confirmado su alineación (o cuando se pasa la
 * fecha límite). Va por {@link AvanceDeJornada}, así que si a la jornada siguiente le faltan estadísticas
 * se simulan antes de puntuar.
 */
@Component
public class LeagueScheduler {

	private static final Logger log = LoggerFactory.getLogger(LeagueScheduler.class);

	private final LeagueRepository leagueRepository;
	private final AvanceDeJornada avanceDeJornada;

	public LeagueScheduler(LeagueRepository leagueRepository, AvanceDeJornada avanceDeJornada) {
		this.leagueRepository = leagueRepository;
		this.avanceDeJornada = avanceDeJornada;
	}

	@Scheduled(fixedDelayString = "${app.league.scheduler-delay-ms}")
	public void avanzarLigasVencidas() {
		for (Long ligaId : leagueRepository.idsAvanzables(LeagueStatus.EN_CURSO, Instant.now(), AdvanceMode.POR_CONFIRMACION)) {
			try {
				avanceDeJornada.avanzar(ligaId, true);
			} catch (ResponseStatusException e) {
				// Lo normal es que la liga todavía no tenga que avanzar: se reintenta en el siguiente ciclo.
				log.debug("Liga {} no avanza todavía: {}", ligaId, e.getReason());
			} catch (RuntimeException e) {
				log.error("Error al avanzar la liga {}", ligaId, e);
			}
		}
	}
}
