package com.ligacomunio.league;

import com.ligacomunio.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class MarketListing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private League league;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Player player;

	@Column(nullable = false)
	private int cycle;
}
