package com.ligacomunio.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final CuentaService cuentaService;
	private final ControlDeIntentos controlDeIntentos;

	public AuthController(AuthService authService, CuentaService cuentaService,
	                      ControlDeIntentos controlDeIntentos) {
		this.authService = authService;
		this.cuentaService = cuentaService;
		this.controlDeIntentos = controlDeIntentos;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse registrar(@Valid @RequestBody RegistroRequest request) {
		AuthResponse respuesta = authService.registrar(request);
		// Fuera de la transacción del registro: si el envío falla, la cuenta ya está creada y
		// siempre se puede pedir otro enlace desde /verify/resend.
		cuentaService.enviarVerificacion(request.email());
		return respuesta;
	}

	@PostMapping("/login")
	public AuthResponse iniciarSesion(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return authService.iniciarSesion(request, http.getRemoteAddr());
	}

	@PostMapping("/refresh")
	public AuthResponse renovar(@Valid @RequestBody RefreshRequest request) {
		return authService.renovar(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cerrarSesion(@Valid @RequestBody RefreshRequest request) {
		authService.cerrarSesion(request.refreshToken());
	}

	@GetMapping("/me")
	public UsuarioDto yo(@AuthenticationPrincipal Jwt jwt) {
		return authService.obtenerUsuario(UsuarioActual.id(jwt));
	}

	@PostMapping("/verify")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verificar(@Valid @RequestBody TokenRequest request) {
		cuentaService.verificar(request.token());
	}

	/** 204 siempre, exista la cuenta o no: la respuesta no debe revelar quién está registrado. */
	@PostMapping("/verify/resend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reenviarVerificacion(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
		limitarEnvios("resend", request.email(), http);
		cuentaService.enviarVerificacion(request.email());
	}

	/** 204 siempre, igual que el reenvío de la verificación. */
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void olvideLaContrasena(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
		limitarEnvios("forgot", request.email(), http);
		cuentaService.solicitarReset(request.email());
	}

	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restablecerContrasena(@Valid @RequestBody ResetRequest request) {
		cuentaService.restablecer(request.token(), request.password());
	}

	/**
	 * Estos dos endpoints mandan correo a una dirección que elige quien llama, así que sin freno
	 * servirían para inundar el buzón de otra persona. Aquí cada petición cuenta como un intento:
	 * pasado el máximo, ese email y esa IP reciben 429 hasta que venza el bloqueo.
	 */
	private void limitarEnvios(String accion, String email, HttpServletRequest http) {
		String[] claves = {accion + ":email:" + email.trim().toLowerCase(Locale.ROOT),
				accion + ":ip:" + http.getRemoteAddr()};
		controlDeIntentos.comprobar(claves);
		controlDeIntentos.fallo(claves);
	}
}
