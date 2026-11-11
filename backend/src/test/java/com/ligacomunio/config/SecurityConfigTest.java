package com.ligacomunio.config;

import com.ligacomunio.auth.AuthController;
import com.ligacomunio.auth.AuthService;
import com.ligacomunio.auth.ControlDeIntentos;
import com.ligacomunio.auth.CuentaService;
import com.ligacomunio.importacion.ImportController;
import com.ligacomunio.importacion.ImportResultado;
import com.ligacomunio.importacion.ImportService;
import com.ligacomunio.player.PlayerController;
import com.ligacomunio.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba las reglas de acceso con la cadena de seguridad real (a diferencia de los demás @WebMvcTest). */
@WebMvcTest(controllers = {PlayerController.class, ImportController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = "app.jwt.secret=una-clave-de-prueba-de-32-bytes-minimo!")
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private PlayerRepository playerRepository;
	@MockitoBean
	private ImportService importService;
	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private CuentaService cuentaService;
	@MockitoBean
	private ControlDeIntentos controlDeIntentos;

	private String tokenFirmado(String rol, long segundosDeVida) {
		Instant caducidad = Instant.now().plusSeconds(segundosDeVida);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject("1").issuedAt(caducidad.minusSeconds(900)).expiresAt(caducidad)
				.claim("role", rol).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	@Test
	void sinTokenLaApiDevuelve401() throws Exception {
		mockMvc.perform(get("/api/players")).andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenGarabatoDevuelve401() throws Exception {
		mockMvc.perform(get("/api/players").header("Authorization", "Bearer esto.no.es-un-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenCaducadoDevuelve401() throws Exception {
		mockMvc.perform(get("/api/players").header("Authorization", "Bearer " + tokenFirmado("USER", -120)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenValidoPermiteLeerJugadores() throws Exception {
		when(playerRepository.buscar(isNull(), isNull())).thenReturn(List.of());

		mockMvc.perform(get("/api/players").header("Authorization", "Bearer " + tokenFirmado("USER", 600)))
				.andExpect(status().isOk());
	}

	@Test
	void unUsuarioNormalNoPuedeImportar() throws Exception {
		mockMvc.perform(post("/api/admin/import/teams").header("Authorization", "Bearer " + tokenFirmado("USER", 600)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unAdminPuedeImportar() throws Exception {
		when(importService.importarEquipos(anyInt())).thenReturn(new ImportResultado("equipos", 0, 0, null, List.of()));

		mockMvc.perform(post("/api/admin/import/teams").header("Authorization", "Bearer " + tokenFirmado("ADMIN", 600)))
				.andExpect(status().isOk());
	}

	@Test
	void sinTokenLaImportacionDevuelve401() throws Exception {
		mockMvc.perform(post("/api/admin/import/teams")).andExpect(status().isUnauthorized());
	}

	@Test
	void conUnJwtSimuladoElRolAdminTambienFunciona() throws Exception {
		when(importService.importarEquipos(anyInt())).thenReturn(new ImportResultado("equipos", 0, 0, null, List.of()));

		mockMvc.perform(post("/api/admin/import/teams")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk());
	}

	@Test
	void elLoginNoRequiereToken() throws Exception {
		// Cuerpo vacío: llega al controlador (400 de validación), no se corta antes con 401.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void elRegistroNoRequiereToken() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void meExigeToken() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	/**
	 * Las cuatro rutas del correo tienen que ser públicas: se usan desde un enlace del email o
	 * cuando el usuario no puede iniciar sesión. El 400 confirma que la petición llega al controlador.
	 */
	@Test
	void lasRutasDeVerificacionYDeContrasenaNoRequierenToken() throws Exception {
		for (String ruta : List.of("/api/auth/verify", "/api/auth/verify/resend",
				"/api/auth/password/forgot", "/api/auth/password/reset")) {
			mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isBadRequest());
		}
	}

	@Test
	void laApiManaLasCabecerasDeSeguridad() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"));
	}
}
