package com.ligacomunio.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "es obligatorio") @Size(max = 254, message = "es demasiado largo") String email,
		@NotBlank(message = "es obligatoria") @Size(max = 128, message = "es demasiado larga") String password) {
}
