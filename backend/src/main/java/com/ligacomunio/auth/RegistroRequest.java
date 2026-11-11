package com.ligacomunio.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
		@NotBlank(message = "es obligatorio")
		@Email(message = "no es un email válido")
		@Size(max = 254, message = "es demasiado largo") String email,
		@NotBlank(message = "es obligatorio")
		@Pattern(regexp = "[A-Za-z0-9_]{3,20}", message = "debe tener de 3 a 20 letras, números o guion bajo") String username,
		// 72 es el máximo que bcrypt tiene en cuenta
		@NotBlank(message = "es obligatoria")
		@Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password) {
}
