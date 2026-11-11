package com.ligacomunio.auth;

/** expiresIn: segundos de vida del access token. */
public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UsuarioDto user) {
}
