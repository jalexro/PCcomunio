package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Token de un solo uso enviado por correo. Solo se guarda el hash SHA-256, nunca el token en claro. */
@Entity
@Table(name = "user_token")
@Getter
@Setter
public class UserToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TokenType type;

	@Column(nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	/** No nulo en cuanto se canjea: un token sirve una sola vez. */
	private Instant usedAt;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();
}
