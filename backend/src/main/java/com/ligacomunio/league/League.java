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
public class League {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser owner;

	@Column(nullable = false, unique = true)
	private String joinCode;

	/** Año de inicio de la temporada de la que salen los partidos y los puntos (2024 = 2024/25). */
	@Column(nullable = false)
	private int season;

	/** Última jornada ya puntuada; 0 = la liga aún no ha empezado. */
	@Column(nullable = false)
	private int currentMatchday;

	/** Se incrementa cada vez que se refresca el mercado. */
	@Column(nullable = false)
	private int marketCycle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LeagueStatus status = LeagueStatus.EN_CURSO;

	/** Qué dispara el avance de jornada (reloj o confirmaciones). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdvanceMode advanceMode = AdvanceMode.POR_TIEMPO;

	/** Ritmo en POR_TIEMPO; plazo para confirmar la alineación en POR_CONFIRMACION. */
	@Column(nullable = false)
	private long matchdayIntervalSeconds;

	@Column(nullable = false)
	private Instant nextAdvanceAt;

	/** Reparto que recibe cada miembro al entrar (lo elige quien crea la liga y vale también para quien se une). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InitialSquadMode initialSquadMode = InitialSquadMode.ALEATORIO;

	/**
	 * Presupuesto con el que entra cada miembro. Manda este y no {@code app.league.initial-budget}, que solo es
	 * el valor que se propone al crear la liga.
	 */
	@Column(nullable = false)
	private BigDecimal initialBudget;

	/** Tope de la suma de valores de los 11 iniciales; solo lo usa ALEATORIO_CON_TOPE (null en las demás). */
	private BigDecimal initialSquadCap;

	/** Lo que se abona a cada miembro por cada punto de su alineación en una jornada. */
	@Column(nullable = false)
	private BigDecimal pointValue = new BigDecimal("100000");

	/** Premio por puesto en la jornada, de 1.º en adelante (ver {@link PremiosPorPosicion}). */
	@Column(nullable = false)
	private String positionBonuses = "3000000,2000000,1000000";

	@Column(nullable = false)
	private Instant createdAt = Instant.now();
}
