package com.ligacomunio.team;

public record TeamDto(Long id, String name, String shortName, String crestUrl) {

	public static TeamDto from(Team team) {
		return new TeamDto(team.getId(), team.getName(), team.getShortName(), team.getCrestUrl());
	}
}
