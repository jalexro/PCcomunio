package com.ligacomunio.auth;

import com.ligacomunio.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private CuentaService cuentaService;
	@MockitoBean
	private ControlDeIntentos controlDeIntentos;

	private void esperaBadRequest(String ruta, String cuerpo) throws Exception {
		mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registroValidoDevuelve201ConLosTokens() throws Exception {
		when(authService.registrar(any())).thenReturn(new AuthResponse("acceso", "refresco", 900,
				new UsuarioDto(1L, "javi@correo.com", "javi", Role.USER, false)));

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "javi@correo.com", "username": "javi", "password": "contrasena1"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").value("acceso"))
				.andExpect(jsonPath("$.user.username").value("javi"))
				.andExpect(jsonPath("$.user.emailVerified").value(false))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());

		verify(cuentaService).enviarVerificacion("javi@correo.com");
	}

	@Test
	void verificarConUnTokenValidoEs204() throws Exception {
		mockMvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON).content("""
						{"token": "abc123"}
						"""))
				.andExpect(status().isNoContent());

		verify(cuentaService).verificar("abc123");
	}

	@Test
	void verificarSinTokenEs400() throws Exception {
		esperaBadRequest("/api/auth/verify", "{}");
	}

	@Test
	void olvideLaContrasenaEs204AunqueLaCuentaNoExista() throws Exception {
		mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "nadie@correo.com"}
						"""))
				.andExpect(status().isNoContent());

		verify(cuentaService).solicitarReset("nadie@correo.com");
	}

	@Test
	void reenviarLaVerificacionEs204() throws Exception {
		mockMvc.perform(post("/api/auth/verify/resend").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "javi@correo.com"}
						"""))
				.andExpect(status().isNoContent());

		verify(cuentaService).enviarVerificacion("javi@correo.com");
	}

	@Test
	void losEnviosDeCorreoPasanPorElControlDeIntentos() throws Exception {
		mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "Javi@Correo.com"}
						"""))
				.andExpect(status().isNoContent());

		// El email se normaliza antes de contar, para que cambiar las mayúsculas no esquive el límite.
		verify(controlDeIntentos).comprobar("forgot:email:javi@correo.com", "forgot:ip:127.0.0.1");
		verify(controlDeIntentos).fallo("forgot:email:javi@correo.com", "forgot:ip:127.0.0.1");
	}

	@Test
	void pasadoElLimiteLosEnviosDeCorreoDevuelven429SinMandarNada() throws Exception {
		doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
				"Demasiados intentos fallidos. Vuelve a probar en 15 minutos."))
				.when(controlDeIntentos).comprobar(any(String[].class));

		mockMvc.perform(post("/api/auth/verify/resend").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "javi@correo.com"}
						"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("Demasiados intentos fallidos. Vuelve a probar en 15 minutos."));

		verifyNoInteractions(cuentaService);
	}

	@Test
	void restablecerConContrasenaCortaEs400() throws Exception {
		esperaBadRequest("/api/auth/password/reset", """
				{"token": "abc123", "password": "corta"}
				""");
	}

	@Test
	void restablecerValidoEs204() throws Exception {
		mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content("""
						{"token": "abc123", "password": "contrasena2"}
						"""))
				.andExpect(status().isNoContent());

		verify(cuentaService).restablecer("abc123", "contrasena2");
	}

	@Test
	void emailInvalidoEs400() throws Exception {
		esperaBadRequest("/api/auth/register", """
				{"email": "no-es-un-email", "username": "javi", "password": "contrasena1"}
				""");
	}

	@Test
	void contrasenaCortaEs400ConElMensajeDelCampo() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "javi@correo.com", "username": "javi", "password": "corta"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Datos no válidos"))
				.andExpect(jsonPath("$.errors.password").value("debe tener entre 8 y 72 caracteres"));
	}

	@Test
	void unConflictoDelServicioLlegaConSuMensaje() throws Exception {
		when(authService.registrar(any())).thenThrow(
				new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "El email ya está registrado"));

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "javi@correo.com", "username": "javi", "password": "contrasena1"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("El email ya está registrado"));
	}

	@Test
	void contrasenaDeMasDe72CaracteresEs400() throws Exception {
		esperaBadRequest("/api/auth/register",
				"{\"email\": \"javi@correo.com\", \"username\": \"javi\", \"password\": \"" + "a".repeat(73) + "\"}");
	}

	@Test
	void nombreDeUsuarioConCaracteresRarosEs400() throws Exception {
		esperaBadRequest("/api/auth/register", """
				{"email": "javi@correo.com", "username": "ja vi!", "password": "contrasena1"}
				""");
	}

	@Test
	void loginSinContrasenaEs400() throws Exception {
		esperaBadRequest("/api/auth/login", """
				{"email": "javi@correo.com"}
				""");
	}

	@Test
	void refreshSinTokenEs400() throws Exception {
		esperaBadRequest("/api/auth/refresh", "{}");
	}
}
