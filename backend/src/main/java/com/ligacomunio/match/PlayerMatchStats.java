package com.ligacomunio.match;

import com.ligacomunio.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Datos brutos de un jugador en un partido; {@code points} lo rellena el ScoringService. */
@Entity
@Table(name = "player_match_stats", uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "match_id"}))
@Getter
@Setter
public class PlayerMatchStats {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Match match;

	private int minutes;
	private int goals;
	private int assists;
	private int tacklesWon;
	private int yellowCards;
	private int redCards;
	private int passesCompleted;
	private int passesFailed;
	private int tirosFallados;

	// Solo porteros
	private int saves;
	private int goalsConceded;
	private int shotsFaced;

	private BigDecimal rating;
	private BigDecimal points;
}
