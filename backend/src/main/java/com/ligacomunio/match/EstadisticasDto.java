package com.ligacomunio.match;

import com.ligacomunio.player.Position;

import java.math.BigDecimal;

public record EstadisticasDto(
		Long playerId,
		String playerName,
		Position position,
		int minutes,
		int goals,
		int assists,
		int tacklesWon,
		int yellowCards,
		int redCards,
		int passesCompleted,
		int passesFailed,
		int tirosFallados,
		int saves,
		int goalsConceded,
		int shotsFaced,
		BigDecimal points) {

	public static EstadisticasDto from(PlayerMatchStats s) {
		return new EstadisticasDto(s.getPlayer().getId(), s.getPlayer().getName(), s.getPlayer().getPosition(),
				s.getMinutes(), s.getGoals(), s.getAssists(), s.getTacklesWon(), s.getYellowCards(), s.getRedCards(),
				s.getPassesCompleted(), s.getPassesFailed(), s.getTirosFallados(), s.getSaves(), s.getGoalsConceded(),
				s.getShotsFaced(), s.getPoints());
	}
}
