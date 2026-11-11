package com.ligacomunio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos aleatorios y su hash. Los tokens (refresh, verificación de email, reset de contraseña)
 * se envían al usuario en claro pero en la base de datos solo vive el hash SHA-256.
 *
 * <p>SHA-256 sin sal basta aquí, al contrario que con las contraseñas: son 256 bits de entropía
 * aleatoria, así que no hay nada que adivinar por fuerza bruta ni por diccionario.
 */
public final class Tokens {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private Tokens() {
	}

	/** Token nuevo de 256 bits, en Base64 seguro para URL (así puede ir en un enlace de correo). */
	public static String nuevo() {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String token) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
