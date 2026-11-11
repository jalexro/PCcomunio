package com.ligacomunio.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

	private final JwtConfig config = new JwtConfig();

	@Test
	void sinSecretoLaAplicacionNoArranca() {
		assertThatThrownBy(() -> config.jwtKey(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void unSecretoCortoSeRechaza() {
		assertThatThrownBy(() -> config.jwtKey("demasiado-corto"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unSecretoDe32CaracteresSeAcepta() {
		assertThat(config.jwtKey("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
	}
}
