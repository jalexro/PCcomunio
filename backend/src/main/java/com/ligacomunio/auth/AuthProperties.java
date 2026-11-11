package com.ligacomunio.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ajustes de seguridad de la autenticación, configurables en application.properties (prefijo app.auth). */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		int maxIntentos,
		long bloqueoSegundos,
		long ventanaSegundos,
		boolean exigirEmailVerificado,
		long verificacionTtlHoras,
		long resetTtlMinutos,
		String urlFrontend,
		String remitente,
		long limpiezaDelayMs) {
}
