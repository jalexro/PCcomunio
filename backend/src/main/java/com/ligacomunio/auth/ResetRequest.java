package com.ligacomunio.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contraseña nueva más el token recibido por correo. */
public record ResetRequest(
		@NotBlank(message = "es obligatorio")
		@Size(max = 200, message = "no es válido") String token,
		// 72 es el máximo que bcrypt tiene en cuenta
		@NotBlank(message = "es obligatoria")
		@Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password) {
}
