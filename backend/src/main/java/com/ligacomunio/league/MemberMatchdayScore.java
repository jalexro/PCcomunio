package com.ligacomunio.league;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class MemberMatchdayScore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private LeagueMember member;

	@Column(nullable = false)
	private int matchday;

	@Column(nullable = false)
	private BigDecimal points;

	/** Puesto en la jornada (los empates comparten puesto). */
	private Integer position;

	/** Abono por los puntos (puntos x valor del punto) y premio por el puesto. */
	@Column(nullable = false)
	private BigDecimal pointsPayout = BigDecimal.ZERO;

	@Column(nullable = false)
	private BigDecimal positionBonus = BigDecimal.ZERO;

	/** Puestos de la táctica que se quedaron sin jugador. */
	@Column(nullable = false)
	private int emptySlots;

	/** Puntos perdidos por esos puestos vacíos (positivo); ya está descontado de {@code points}. */
	@Column(nullable = false)
	private BigDecimal emptySlotPenalty = BigDecimal.ZERO;
}
