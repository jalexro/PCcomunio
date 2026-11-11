package com.ligacomunio.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Para pedir el reenvío de la verificación o el enlace de reset. */
public record EmailRequest(
		@NotBlank(message = "es obligatorio")
		@Email(message = "no es un email válido")
		@Size(max = 254, message = "es demasiado largo") String email) {
}
