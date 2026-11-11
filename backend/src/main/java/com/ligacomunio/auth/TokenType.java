package com.ligacomunio.auth;

public enum TokenType {
	/** Confirma que el email existe y es del usuario. */
	VERIFICACION,
	/** Permite fijar una contraseña nueva sin saber la anterior. */
	RESET
}
