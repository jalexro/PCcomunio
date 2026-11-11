package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.Role;
import com.ligacomunio.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

	private static final String CREDENCIALES_INCORRECTAS = "Credenciales incorrectas";
	private static final String SESION_NO_VALIDA = "Sesión no válida";

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final ControlDeIntentos controlDeIntentos;
	private final boolean exigirEmailVerificado;
	private final Duration refreshTtl;
	/** Se compara contra este hash cuando el email no existe, para que login tarde lo mismo en ambos casos. */
	private final String hashFalso;

	public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
	                   PasswordEncoder passwordEncoder, JwtService jwtService,
	                   ControlDeIntentos controlDeIntentos, AuthProperties propiedades,
	                   @Value("${app.jwt.refresh-ttl-days:30}") long refreshTtlDias) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.controlDeIntentos = controlDeIntentos;
		this.exigirEmailVerificado = propiedades.exigirEmailVerificado();
		this.refreshTtl = Duration.ofDays(refreshTtlDias);
		this.hashFalso = passwordEncoder.encode("contraseña-que-nadie-usa");
	}

	@Transactional
	public AuthResponse registrar(RegistroRequest request) {
		String email = normalizar(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
		}
		if (userRepository.existsByUsernameIgnoreCase(request.username())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
		}
		AppUser usuario = new AppUser();
		usuario.setEmail(email);
		usuario.setUsername(request.username());
		usuario.setPasswordHash(passwordEncoder.encode(request.password()));
		usuario.setRole(Role.USER);
		// El correo de verificación lo envía después el controlador, ya fuera de esta transacción.
		usuario.setEmailVerified(false);
		return emitir(userRepository.save(usuario));
	}

	/**
	 * Cada fallo se apunta en {@link ControlDeIntentos} con dos claves, el email y la IP, y al llegar al
	 * máximo ese email o esa IP reciben 429 un rato. El bloqueo se comprueba antes de tocar la base de datos.
	 */
	@Transactional
	public AuthResponse iniciarSesion(LoginRequest request, String ip) {
		String email = normalizar(request.email());
		String[] claves = claves(email, ip);
		controlDeIntentos.comprobar(claves);

		AppUser usuario = userRepository.findByEmail(email).orElse(null);
		String hash = usuario != null ? usuario.getPasswordHash() : hashFalso;
		boolean correcta = passwordEncoder.matches(request.password(), hash);
		if (usuario == null || !correcta) {
			controlDeIntentos.fallo(claves);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
		}
		// Una contraseña correcta limpia el contador aunque la cuenta siga sin verificar: no es un ataque.
		controlDeIntentos.exito(claves);
		if (exigirEmailVerificado && !usuario.isEmailVerified()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Tienes que confirmar tu email antes de entrar. Revisa tu correo o pide otro enlace.");
		}
		return emitir(usuario);
	}

	/**
	 * Rotación: el refresh token usado se revoca y se entrega uno nuevo. Si llega uno ya revocado
	 * (alguien lo reutiliza: posible robo) se revocan todos los del usuario. noRollbackFor es necesario
	 * para que esa revocación se confirme aunque después se lance el 401.
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public AuthResponse renovar(String refreshToken) {
		RefreshToken guardado = refreshTokenRepository.buscarPorHash(Tokens.hash(refreshToken))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESION_NO_VALIDA));
		if (guardado.isRevoked()) {
			refreshTokenRepository.revocarTodosDelUsuario(guardado.getUser().getId());
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESION_NO_VALIDA);
		}
		if (guardado.getExpiresAt().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESION_NO_VALIDA);
		}
		guardado.setRevoked(true);
		return emitir(guardado.getUser());
	}

	/** Idempotente: cerrar una sesión que no existe o ya está cerrada no es un error. */
	@Transactional
	public void cerrarSesion(String refreshToken) {
		refreshTokenRepository.buscarPorHash(Tokens.hash(refreshToken)).ifPresent(t -> t.setRevoked(true));
	}

	@Transactional(readOnly = true)
	public UsuarioDto obtenerUsuario(Long id) {
		return userRepository.findById(id).map(UsuarioDto::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESION_NO_VALIDA));
	}

	private AuthResponse emitir(AppUser usuario) {
		String refreshToken = Tokens.nuevo();

		RefreshToken guardado = new RefreshToken();
		guardado.setUser(usuario);
		guardado.setTokenHash(Tokens.hash(refreshToken));
		guardado.setExpiresAt(Instant.now().plus(refreshTtl));
		refreshTokenRepository.save(guardado);

		return new AuthResponse(jwtService.crearAccessToken(usuario), refreshToken,
				jwtService.accessTtlSegundos(), UsuarioDto.from(usuario));
	}

	private String[] claves(String email, String ip) {
		return ip == null || ip.isBlank()
				? new String[]{"login:email:" + email}
				: new String[]{"login:email:" + email, "login:ip:" + ip};
	}

	private String normalizar(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
