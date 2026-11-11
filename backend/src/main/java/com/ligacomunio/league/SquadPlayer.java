package com.ligacomunio.league;

import com.ligacomunio.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
public class SquadPlayer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private League league;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private LeagueMember member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Player player;

	@Column(nullable = false)
	private boolean starter;

	@Column(nullable = false)
	private BigDecimal pricePaid = BigDecimal.ZERO;

	@Column(nullable = false)
	private Instant acquiredAt = Instant.now();
}
