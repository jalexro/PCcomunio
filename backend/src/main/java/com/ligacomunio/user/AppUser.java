package com.ligacomunio.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Siempre en minúsculas. */
	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.USER;

	/** Con app.auth.exigir-email-verificado=true, el login solo funciona si esto es true. */
	@Column(nullable = false)
	private boolean emailVerified;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();
}
