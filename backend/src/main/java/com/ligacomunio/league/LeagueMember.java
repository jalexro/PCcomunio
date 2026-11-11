package com.ligacomunio.league;

import com.ligacomunio.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
public class LeagueMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private League league;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@Column(nullable = false)
	private BigDecimal budget;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Formation formation = Formation.F442;

	@Column(nullable = false)
	private BigDecimal totalPoints = BigDecimal.ZERO;

	/**
	 * Jornada para la que ya se ha confirmado la alineación. Se compara con {@code currentMatchday + 1},
	 * de modo que al avanzar la liga las confirmaciones caducan solas.
	 */
	@Column(nullable = false)
	private int lineupConfirmedMatchday;

	@Column(nullable = false)
	private Instant joinedAt = Instant.now();
}
