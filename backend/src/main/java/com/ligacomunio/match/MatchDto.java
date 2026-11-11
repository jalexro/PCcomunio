package com.ligacomunio.match;

import java.time.Instant;

public record MatchDto(
		Long id,
		int matchday,
		Instant kickoff,
		Long homeTeamId,
		String homeTeamName,
		Long awayTeamId,
		String awayTeamName,
		Integer homeGoals,
		Integer awayGoals,
		MatchStatus status) {

	public static MatchDto from(Match m) {
		return new MatchDto(m.getId(), m.getMatchday(), m.getKickoff(),
				m.getHomeTeam().getId(), m.getHomeTeam().getName(),
				m.getAwayTeam().getId(), m.getAwayTeam().getName(),
				m.getHomeGoals(), m.getAwayGoals(), m.getStatus());
	}
}
