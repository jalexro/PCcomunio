package com.ligacomunio.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** El id del usuario autenticado, que va en el claim "sub" del access token. */
public final class UsuarioActual {

	private UsuarioActual() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
