package com.ligacomunio.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Las tablas de tokens solo crecen: cada login deja un refresh token y cada reset un user_token.
 * Esta tarea borra los que ya no sirven para nada.
 */
@Component
public class LimpiezaDeTokens {

	private static final Logger log = LoggerFactory.getLogger(LimpiezaDeTokens.class);
	/** Margen tras la caducidad: un refresh token revocado sigue delatando una reutilización reciente. */
	private static final Duration MARGEN = Duration.ofDays(7);

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserTokenRepository userTokenRepository;
	private final ControlDeIntentos controlDeIntentos;

	public LimpiezaDeTokens(RefreshTokenRepository refreshTokenRepository,
	                        UserTokenRepository userTokenRepository, ControlDeIntentos controlDeIntentos) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userTokenRepository = userTokenRepository;
		this.controlDeIntentos = controlDeIntentos;
	}

	@Scheduled(fixedDelayString = "${app.auth.limpieza-delay-ms}", initialDelayString = "${app.auth.limpieza-delay-ms}")
	@Transactional
	public void limpiar() {
		Instant limite = Instant.now().minus(MARGEN);
		int refresh = refreshTokenRepository.borrarCaducados(limite);
		int correo = userTokenRepository.borrarCaducados(limite);
		controlDeIntentos.limpiar();
		if (refresh + correo > 0) {
			log.info("Limpieza de tokens: {} refresh y {} de correo", refresh, correo);
		}
	}
}
