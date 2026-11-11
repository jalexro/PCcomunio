package com.ligacomunio.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token recibido por correo (verificación de email). */
public record TokenRequest(
		@NotBlank(message = "es obligatorio")
		@Size(max = 200, message = "no es válido") String token) {
}
