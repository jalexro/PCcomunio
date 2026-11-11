package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserTokenRepository userTokenRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;
	@Mock
	private Mailer mailer;

	private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder(4);
	private CuentaService servicio;

	@BeforeEach
	void preparar() {
		AuthProperties propiedades = new AuthProperties(5, 900, 900, false, 48, 30,
				"https://ligacomunio.test", "no-reply@test", 3600000);
		servicio = new CuentaService(userRepository, userTokenRepository, refreshTokenRepository,
				codificador, mailer, propiedades);
	}

	private AppUser usuario(boolean verificado) {
		AppUser u = new AppUser();
		ReflectionTestUtils.setField(u, "id", 7L);
		u.setEmail("javi@correo.com");
		u.setUsername("javi");
		u.setPasswordHash(codificador.encode("contrasena1"));
		u.setEmailVerified(verificado);
		return u;
	}

	private UserToken token(AppUser dueno, TokenType tipo, Instant caduca, Instant usado) {
		UserToken t = new UserToken();
		t.setUser(dueno);
		t.setType(tipo);
		t.setExpiresAt(caduca);
		t.setUsedAt(usado);
		return t;
	}

	// --- Verificación de email ---

	@Test
	void laVerificacionManaUnEnlaceConElTokenEnClaroYGuardaSoloElHash() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario(false)));

		servicio.enviarVerificacion("  Javi@Correo.com ");

		ArgumentCaptor<UserToken> guardado = ArgumentCaptor.forClass(UserToken.class);
		verify(userTokenRepository).save(guardado.capture());
		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(mailer).enviar(eq("javi@correo.com"), anyString(), cuerpo.capture());

		assertThat(guardado.getValue().getType()).isEqualTo(TokenType.VERIFICACION);
		assertThat(guardado.getValue().getTokenHash()).hasSize(64);
		assertThat(cuerpo.getValue()).contains("https://ligacomunio.test/verificar-email?token=");
		// El hash guardado se corresponde con el token del enlace, y el enlace no lleva el hash.
		String enlace = cuerpo.getValue().substring(cuerpo.getValue().indexOf("token=") + 6).split("\\s")[0];
		assertThat(Tokens.hash(enlace)).isEqualTo(guardado.getValue().getTokenHash());
	}

	@Test
	void noSeEnviaNadaSiLaCuentaNoExiste() {
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

		servicio.enviarVerificacion("nadie@correo.com"); // Silencioso: no revela que no existe

		verifyNoInteractions(mailer);
		verify(userTokenRepository, never()).save(any());
	}

	@Test
	void noSeReenviaLaVerificacionSiElEmailYaEstaConfirmado() {
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario(true)));

		servicio.enviarVerificacion("javi@correo.com");

		verifyNoInteractions(mailer);
	}

	@Test
	void emitirUnTokenNuevoInvalidaLosAnteriores() {
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario(false)));

		servicio.enviarVerificacion("javi@correo.com");

		verify(userTokenRepository).gastarAnteriores(eq(7L), eq(TokenType.VERIFICACION), any(Instant.class));
	}

	@Test
	void verificarMarcaLaCuentaYGastaElToken() {
		AppUser sinVerificar = usuario(false);
		UserToken valido = token(sinVerificar, TokenType.VERIFICACION, Instant.now().plusSeconds(3600), null);
		when(userTokenRepository.buscar(anyString(), eq(TokenType.VERIFICACION))).thenReturn(Optional.of(valido));

		servicio.verificar("el-token");

		assertThat(sinVerificar.isEmailVerified()).isTrue();
		assertThat(valido.getUsedAt()).isNotNull();
	}

	@Test
	void unTokenYaUsadoNoVuelveAValer() {
		UserToken usado = token(usuario(false), TokenType.VERIFICACION, Instant.now().plusSeconds(3600), Instant.now());
		when(userTokenRepository.buscar(anyString(), eq(TokenType.VERIFICACION))).thenReturn(Optional.of(usado));

		assertThatThrownBy(() -> servicio.verificar("el-token"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void unTokenCaducadoNoVale() {
		UserToken caducado = token(usuario(false), TokenType.VERIFICACION, Instant.now().minusSeconds(1), null);
		when(userTokenRepository.buscar(anyString(), eq(TokenType.VERIFICACION))).thenReturn(Optional.of(caducado));

		assertThatThrownBy(() -> servicio.verificar("el-token"))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void unTokenDesconocidoNoVale() {
		when(userTokenRepository.buscar(anyString(), eq(TokenType.VERIFICACION))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.verificar("inventado"))
				.isInstanceOf(ResponseStatusException.class);
	}

	// --- Recuperación de contraseña ---

	@Test
	void elResetManaUnEnlaceDistintoDelDeVerificacion() {
		when(userRepository.findByEmail("javi@correo.com")).thenReturn(Optional.of(usuario(true)));

		servicio.solicitarReset("javi@correo.com");

		ArgumentCaptor<UserToken> guardado = ArgumentCaptor.forClass(UserToken.class);
		verify(userTokenRepository).save(guardado.capture());
		assertThat(guardado.getValue().getType()).isEqualTo(TokenType.RESET);

		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(mailer).enviar(eq("javi@correo.com"), anyString(), cuerpo.capture());
		assertThat(cuerpo.getValue()).contains("https://ligacomunio.test/restablecer?token=");
	}

	@Test
	void pedirResetDeUnaCuentaInexistenteNoEnviaNadaNiFalla() {
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

		servicio.solicitarReset("nadie@correo.com");

		verifyNoInteractions(mailer);
	}

	@Test
	void restablecerCambiaLaContrasenaYCierraLasSesionesAbiertas() {
		AppUser dueno = usuario(true);
		String hashViejo = dueno.getPasswordHash();
		UserToken valido = token(dueno, TokenType.RESET, Instant.now().plusSeconds(600), null);
		when(userTokenRepository.buscar(anyString(), eq(TokenType.RESET))).thenReturn(Optional.of(valido));

		servicio.restablecer("el-token", "contrasenaNueva1");

		assertThat(dueno.getPasswordHash()).isNotEqualTo(hashViejo);
		assertThat(codificador.matches("contrasenaNueva1", dueno.getPasswordHash())).isTrue();
		assertThat(valido.getUsedAt()).isNotNull();
		verify(refreshTokenRepository).revocarTodosDelUsuario(7L);
	}

	@Test
	void restablecerDaLaCuentaPorVerificada() {
		AppUser sinVerificar = usuario(false);
		UserToken valido = token(sinVerificar, TokenType.RESET, Instant.now().plusSeconds(600), null);
		when(userTokenRepository.buscar(anyString(), eq(TokenType.RESET))).thenReturn(Optional.of(valido));

		servicio.restablecer("el-token", "contrasenaNueva1");

		// Quien abre el enlace tiene acceso al buzón, que es justo lo que la verificación comprueba.
		assertThat(sinVerificar.isEmailVerified()).isTrue();
	}

	@Test
	void unTokenDeVerificacionNoSirveParaRestablecerLaContrasena() {
		when(userTokenRepository.buscar(anyString(), eq(TokenType.RESET))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.restablecer("token-de-verificacion", "contrasenaNueva1"))
				.isInstanceOf(ResponseStatusException.class);
		verify(refreshTokenRepository, never()).revocarTodosDelUsuario(anyLong());
	}
}
