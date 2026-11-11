package com.ligacomunio.league;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SquadPlayerRepository extends JpaRepository<SquadPlayer, Long> {

	@Query("select s from SquadPlayer s join fetch s.player p join fetch p.team where s.member.id = :memberId")
	List<SquadPlayer> plantilla(@Param("memberId") Long memberId);

	long countByMemberId(Long memberId);

	boolean existsByLeagueIdAndPlayerId(Long leagueId, Long playerId);

	@Query("select s from SquadPlayer s join fetch s.member where s.league.id = :leagueId and s.player.id = :playerId")
	Optional<SquadPlayer> dueno(@Param("leagueId") Long leagueId, @Param("playerId") Long playerId);

	@Query("select s from SquadPlayer s where s.league.id = :leagueId and s.starter = true")
	List<SquadPlayer> titularesDeLiga(@Param("leagueId") Long leagueId);

	@Query("select s.player.id from SquadPlayer s where s.league.id = :leagueId")
	Set<Long> idsDeJugadores(@Param("leagueId") Long leagueId);
}
