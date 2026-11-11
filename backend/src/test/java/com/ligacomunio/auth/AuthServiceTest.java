package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.Role;
import com.ligacomunio.user.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder(4);
	private AuthService servicio;

	/** Las mismas propiedades para todos los tests salvo el interruptor de email verificado. */
	private static AuthProperties propiedades(boolean exigirEmailVerificado) {
		return new AuthProperties(3, 900, 900, exigirEmailVerificado, 48, 30,
				"http://localhost:5173", "no-reply@test", 3600000);
	}

	@BeforeEach
	void preparar() {
		servicio = construir(propiedades(false));
	}

	private AuthService construir(AuthProperties propiedades) {
		SecretKeySpec clave = new SecretKeySpec("una-clave-de-prueba-de-32-bytes-minimo!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtService jwtService = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(clave)), 900);
		return new AuthService(userRepository, refreshTokenRepository, codificador, jwtService,
				new ControlDeIntentos(propiedades), propiedades, 30);
	}

	private AppUser usuario(String email, String contrasena) {
		AppUser u = new AppUser();
		ReflectionTestUtils.setField(u, "id", 7L);
		u.setEmail(email);
		u.setUsername("javi");
		u.setPasswordHash(codificador.encode(contrasena));
		u.setEmailVerified(true);
		return u;
	}

	private RefreshToken tokenGuardado(AppUser dueno, boolean revocado, Instant caduca) {
		RefreshToken t = new RefreshToken();
		t.setUser(dueno);
		t.setRevoked(revocado);
		t.setExpiresAt(caduca);
		return t;
	}

	@Test
	void registraConEmailEnMinusculasContrasenaCifradaYRolUser() {
		when(userRepository.existsByEmail("javi@correo.com")).thenReturn(false);
		when(userRepository.existsByUsernameIgnoreCase("javi")).thenReturn(false);
		when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
			AppUser u = inv.getArgument(0);
			ReflectionTestUtils.setField(u, "id", 7L);
			return u;
		});

		AuthResponse respuesta = servicio.registrar(new RegistroRequest("  Javi@Correo.com ", "javi", "contrasena1"));

		ArgumentCaptor<AppUser> guardado = ArgumentCaptor.forClass(AppUser.class);
		verify(userRepository).save(guardado.capture());
		assertThat(guardado.getValue().getEmail()).isEqualTo("javi@correo.com");
		assertThat(guardado.getValue().getRole()).isEqualTo(Role.USER);
		assertThat(guardado.getValue().getPasswordHash()).isNotEqualTo("contrasena1");
		assertThat(codificador.matches("contrasena1", guardado.getValue().getPasswordHash())).isTrue();
		assertThat(respuesta.accessToken()).isNotBlank();
		assertThat(respuesta.refreshToken()).isNotBlank();
		assertThat(respuesta.expiresIn()).isEqualTo(900);
	}

	@Test
	void elRefreshTokenSeGuardaCifradoYNoEnClaro() {
		when(userRepository.existsByEmail(anyString())).thenReturn(false);
		when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
		when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

		AuthResponse respuesta = servicio.registrar(new RegistroRequest("a@b.com", "javi", "contrasena1"));

		ArgumentCaptor<RefreshToken> guardado = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(guardado.capture());
		assertThat(guardado.getValue().getTokenHash()).hasSize(64).isNotEqualTo(respuesta.refreshToken());
		assertThat(guardado.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(29L * 24 * 3600));
	}

	@Test
	void rechazaEmailDuplicadoConConflicto() {
		when(userRepository.existsByEmail("javi@correo.com")).thenReturn(true);

		assertThatThrownBy(() -> servicio.registrar(new RegistroRequest("javi@correo.com", "javi", "contrasena1")))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
		verify(userRepository, never()).save(any());
	}

	@Test
	void rechazaNombreDeUsuarioDuplicadoSinImportarMayusculas() {
		when(userRepository.existsByEmail(anyString())).thenReturn(false);
		when(userRepository.existsByUsernameIgnoreCase("JAVI")).thenReturn(true);

		assertThatThrownBy(() -> servicio.registrar(new RegistroRequest("otro@correo.com", "JAVI", "contrasena1")))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void loginCorrectoDevuelveTokens() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario("javi@correo.com", "contrasena1")));

		AuthResponse respuesta = servicio.iniciarSesion(new LoginRequest("Javi@correo.com", "contrasena1"), "10.0.0.1");

		assertThat(respuesta.user().username()).isEqualTo("javi");
		assertThat(respuesta.accessToken()).isNotBlank();
	}

	@Test
	void loginConContrasenaIncorrectaYConEmailInexistenteDanElMismoError() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario("javi@correo.com", "contrasena1")));
		when(userRepository.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "mala"), "10.0.0.1"))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
					assertThat(e.getReason()).isEqualTo("Credenciales incorrectas");
				});
		assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("nadie@correo.com", "contrasena1"), "10.0.0.2"))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
					assertThat(e.getReason()).isEqualTo("Credenciales incorrectas");
				});
	}

	@Test
	void trasVariosFallosElLoginSeBloqueaConDemasiadasPeticiones() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario("javi@correo.com", "contrasena1")));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "mala"), "10.0.0.1"))
					.isInstanceOfSatisfying(ResponseStatusException.class,
							e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		}

		// El cuarto intento ni siquiera comprueba la contraseña: la correcta también recibe 429.
		assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "contrasena1"), "10.0.0.1"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
	}

	@Test
	void elBloqueoPorEmailNoAlcanzaAOtraCuentaDesdeOtraIp() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario("javi@correo.com", "contrasena1")));
		when(userRepository.findByEmail("otra@correo.com")).thenReturn(Optional.of(usuario("otra@correo.com", "contrasena2")));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "mala"), "10.0.0.1"))
					.isInstanceOf(ResponseStatusException.class);
		}

		assertThat(servicio.iniciarSesion(new LoginRequest("otra@correo.com", "contrasena2"), "10.0.0.9").accessToken())
				.isNotBlank();
	}

	@Test
	void unLoginCorrectoOlvidaLosFallosAnteriores() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario("javi@correo.com", "contrasena1")));

		assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "mala"), "10.0.0.1"))
				.isInstanceOf(ResponseStatusException.class);
		assertThat(servicio.iniciarSesion(new LoginRequest("javi@correo.com", "contrasena1"), "10.0.0.1")).isNotNull();

		// Si el contador no se hubiera reiniciado, dos fallos más bastarían para bloquear.
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("javi@correo.com", "mala"), "10.0.0.1"))
					.isInstanceOfSatisfying(ResponseStatusException.class,
							e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		}
	}

	@Test
	void conEmailSinVerificarYLaOpcionActivaElLoginDevuelveProhibido() {
		AuthService estricto = construir(propiedades(true));
		AppUser sinVerificar = usuario("javi@correo.com", "contrasena1");
		sinVerificar.setEmailVerified(false);
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(sinVerificar));

		assertThatThrownBy(() -> estricto.iniciarSesion(new LoginRequest("javi@correo.com", "contrasena1"), "10.0.0.1"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void sinLaOpcionActivaUnEmailSinVerificarPuedeEntrar() {
		AppUser sinVerificar = usuario("javi@correo.com", "contrasena1");
		sinVerificar.setEmailVerified(false);
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(sinVerificar));

		assertThat(servicio.iniciarSesion(new LoginRequest("javi@correo.com", "contrasena1"), "10.0.0.1").accessToken())
				.isNotBlank();
	}

	@Test
	void elRegistroDejaLaCuentaSinVerificar() {
		when(userRepository.existsByEmail(anyString())).thenReturn(false);
		when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
		when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

		AuthResponse respuesta = servicio.registrar(new RegistroRequest("a@b.com", "javi", "contrasena1"));

		assertThat(respuesta.user().emailVerified()).isFalse();
	}

	@Test
	void renovarRevocaElTokenUsadoYEntregaUnoNuevo() {
		RefreshToken usado = tokenGuardado(usuario("javi@correo.com", "contrasena1"), false, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(usado));

		AuthResponse respuesta = servicio.renovar("token-viejo");

		assertThat(usado.isRevoked()).isTrue();
		assertThat(respuesta.refreshToken()).isNotBlank().isNotEqualTo("token-viejo");
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void reutilizarUnTokenRevocadoRevocaTodasLasSesionesDelUsuario() {
		RefreshToken robado = tokenGuardado(usuario("javi@correo.com", "contrasena1"), true, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(robado));

		assertThatThrownBy(() -> servicio.renovar("token-reutilizado"))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

		verify(refreshTokenRepository).revocarTodosDelUsuario(7L);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void unTokenCaducadoNoSeRenueva() {
		RefreshToken caducado = tokenGuardado(usuario("javi@correo.com", "contrasena1"), false, Instant.now().minusSeconds(1));
		when(refreshTokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(caducado));

		assertThatThrownBy(() -> servicio.renovar("token-caducado"))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		assertThat(caducado.isRevoked()).isFalse();
	}

	@Test
	void unTokenDesconocidoNoSeRenueva() {
		when(refreshTokenRepository.buscarPorHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.renovar("inventado"))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void cerrarSesionRevocaElTokenYEsIdempotente() {
		RefreshToken activo = tokenGuardado(usuario("javi@correo.com", "contrasena1"), false, Instant.now().plusSeconds(3600));

		servicio.cerrarSesion("token-que-no-existe"); // Mockito devuelve Optional.empty(): no lanza
		when(refreshTokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(activo));
		servicio.cerrarSesion("token-valido");

		assertThat(activo.isRevoked()).isTrue();
	}
}
