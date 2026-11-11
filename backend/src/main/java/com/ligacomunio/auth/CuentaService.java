package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Verificación del email y recuperación de contraseña. Las dos operaciones que reciben un email
 * ({@link #enviarVerificacion} y {@link #solicitarReset}) no devuelven nada y no fallan cuando la
 * cuenta no existe: si distinguieran los dos casos servirían para averiguar quién está registrado.
 */
@Service
public class CuentaService {

	private static final Logger log = LoggerFactory.getLogger(CuentaService.class);
	private static final String TOKEN_NO_VALIDO = "El enlace no es válido o ha caducado";

	private final UserRepository userRepository;
	private final UserTokenRepository userTokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final Mailer mailer;
	private final AuthProperties propiedades;

	public CuentaService(UserRepository userRepository, UserTokenRepository userTokenRepository,
	                     RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
	                     Mailer mailer, AuthProperties propiedades) {
		this.userRepository = userRepository;
		this.userTokenRepository = userTokenRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailer = mailer;
		this.propiedades = propiedades;
	}

	/** Envía (o reenvía) el enlace de verificación. Silencioso si la cuenta no existe o ya está verificada. */
	@Transactional
	public void enviarVerificacion(String email) {
		Optional<AppUser> encontrado = userRepository.findByEmail(normalizar(email));
		if (encontrado.isEmpty() || encontrado.get().isEmailVerified()) {
			return;
		}
		AppUser usuario = encontrado.get();
		String token = emitir(usuario, TokenType.VERIFICACION,
				Duration.ofHours(propiedades.verificacionTtlHoras()));
		mailer.enviar(usuario.getEmail(), "Confirma tu cuenta de Liga Comunio", """
				Hola, %s:

				Confirma tu dirección de correo abriendo este enlace:
				%s/verificar-email?token=%s

				El enlace caduca en %d horas. Si no te has registrado, ignora este mensaje.""".formatted(
				usuario.getUsername(), propiedades.urlFrontend(), token, propiedades.verificacionTtlHoras()));
	}

	@Transactional
	public void verificar(String token) {
		UserToken guardado = canjear(token, TokenType.VERIFICACION);
		guardado.getUser().setEmailVerified(true);
		log.info("Email verificado: {}", guardado.getUser().getEmail());
	}

	/** Envía el enlace para restablecer la contraseña. Silencioso si la cuenta no existe. */
	@Transactional
	public void solicitarReset(String email) {
		Optional<AppUser> encontrado = userRepository.findByEmail(normalizar(email));
		if (encontrado.isEmpty()) {
			return;
		}
		AppUser usuario = encontrado.get();
		String token = emitir(usuario, TokenType.RESET, Duration.ofMinutes(propiedades.resetTtlMinutos()));
		mailer.enviar(usuario.getEmail(), "Restablece tu contraseña de Liga Comunio", """
				Hola, %s:

				Para elegir una contraseña nueva, abre este enlace:
				%s/restablecer?token=%s

				El enlace caduca en %d minutos y solo se puede usar una vez.
				Si no has pedido cambiar la contraseña, ignora este mensaje: la actual sigue siendo válida.""".formatted(
				usuario.getUsername(), propiedades.urlFrontend(), token, propiedades.resetTtlMinutos()));
	}

	/**
	 * Fija la contraseña nueva y cierra todas las sesiones abiertas: si alguien había entrado con la
	 * contraseña antigua, el reset lo echa fuera.
	 */
	@Transactional
	public void restablecer(String token, String nuevaPassword) {
		UserToken guardado = canjear(token, TokenType.RESET);
		AppUser usuario = guardado.getUser();
		usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
		// Quien llega hasta aquí ha demostrado tener acceso al email, así que la cuenta queda verificada.
		usuario.setEmailVerified(true);
		refreshTokenRepository.revocarTodosDelUsuario(usuario.getId());
		log.info("Contraseña restablecida: {}", usuario.getEmail());
	}

	/** Marca como gastados los tokens anteriores del mismo tipo y devuelve el nuevo en claro. */
	private String emitir(AppUser usuario, TokenType tipo, Duration ttl) {
		userTokenRepository.gastarAnteriores(usuario.getId(), tipo, Instant.now());
		String token = Tokens.nuevo();
		UserToken guardado = new UserToken();
		guardado.setUser(usuario);
		guardado.setType(tipo);
		guardado.setTokenHash(Tokens.hash(token));
		guardado.setExpiresAt(Instant.now().plus(ttl));
		userTokenRepository.save(guardado);
		return token;
	}

	/** Comprueba un token y lo deja gastado. El mismo 400 para token inexistente, usado y caducado. */
	private UserToken canjear(String token, TokenType tipo) {
		UserToken guardado = userTokenRepository.buscar(Tokens.hash(token), tipo)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, TOKEN_NO_VALIDO));
		if (guardado.getUsedAt() != null || guardado.getExpiresAt().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TOKEN_NO_VALIDO);
		}
		guardado.setUsedAt(Instant.now());
		return guardado;
	}

	private String normalizar(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
