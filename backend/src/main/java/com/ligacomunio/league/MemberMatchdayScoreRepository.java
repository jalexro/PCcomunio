package com.ligacomunio.league;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberMatchdayScoreRepository extends JpaRepository<MemberMatchdayScore, Long> {

	/** Lo abonado a cada miembro de la liga en toda la temporada: filas [memberId, abono por puntos, premio por puesto]. */
	@Query("select s.member.id, sum(s.pointsPayout), sum(s.positionBonus) from MemberMatchdayScore s where s.member.league.id = :leagueId group by s.member.id")
	List<Object[]> abonosAcumulados(@Param("leagueId") Long leagueId);

	@Query("select s from MemberMatchdayScore s where s.member.league.id = :leagueId and s.matchday = :matchday")
	List<MemberMatchdayScore> deJornada(@Param("leagueId") Long leagueId, @Param("matchday") int matchday);
}
