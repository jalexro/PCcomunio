package com.ligacomunio.player;

import com.ligacomunio.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Position position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Team team;

	@Column(nullable = false)
	private BigDecimal marketValue = BigDecimal.ZERO;

	private String photoUrl;

	/** Identificador en API-Football; null en jugadores creados a mano. */
	private Long externalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlayerStatus status = PlayerStatus.DISPONIBLE;
}
