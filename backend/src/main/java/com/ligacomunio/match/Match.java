package com.ligacomunio.match;

import com.ligacomunio.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "matches")
@Getter
@Setter
public class Match {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private int matchday;

	@Column(nullable = false)
	private Instant kickoff;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Team homeTeam;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Team awayTeam;

	/** Año de inicio de la temporada (2024 = 2024/25). */
	private int season = 2024;

	/** Identificador en API-Football; null en partidos creados a mano. */
	private Long externalId;

	private Integer homeGoals;

	private Integer awayGoals;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MatchStatus status = MatchStatus.PROGRAMADO;
}
