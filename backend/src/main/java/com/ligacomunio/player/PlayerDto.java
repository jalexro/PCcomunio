package com.ligacomunio.player;

import java.math.BigDecimal;

public record PlayerDto(
		Long id,
		String name,
		Position position,
		PlayerStatus status,
		BigDecimal marketValue,
		String photoUrl,
		Long teamId,
		String teamName,
		String teamShortName) {

	public static PlayerDto from(Player p) {
		return new PlayerDto(p.getId(), p.getName(), p.getPosition(), p.getStatus(), p.getMarketValue(),
				p.getPhotoUrl(), p.getTeam().getId(), p.getTeam().getName(), p.getTeam().getShortName());
	}
}
