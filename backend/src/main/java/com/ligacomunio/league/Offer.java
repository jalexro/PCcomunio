package com.ligacomunio.league;

import com.ligacomunio.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Puja al mercado (seller == null) u oferta directa al dueño del jugador (seller != null). */
@Entity
@Getter
@Setter
public class Offer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private League league;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private LeagueMember bidder;

	@ManyToOne(fetch = FetchType.LAZY)
	private LeagueMember seller;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OfferStatus status = OfferStatus.PENDIENTE;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant resolvedAt;

	public void resolver(OfferStatus nuevoEstado) {
		this.status = nuevoEstado;
		this.resolvedAt = Instant.now();
	}
}
