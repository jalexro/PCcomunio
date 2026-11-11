package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.Role;

public record UsuarioDto(Long id, String email, String username, Role role, boolean emailVerified) {

	public static UsuarioDto from(AppUser u) {
		return new UsuarioDto(u.getId(), u.getEmail(), u.getUsername(), u.getRole(), u.isEmailVerified());
	}
}
