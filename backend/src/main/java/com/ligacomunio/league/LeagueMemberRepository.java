package com.ligacomunio.league;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, Long> {

	Optional<LeagueMember> findByLeagueIdAndUserId(Long leagueId, Long userId);

	Optional<LeagueMember> findByIdAndLeagueId(Long id, Long leagueId);

	long countByLeagueId(Long leagueId);

	@Query("select m from LeagueMember m join fetch m.user where m.league.id = :leagueId order by m.totalPoints desc, m.joinedAt")
	List<LeagueMember> clasificados(@Param("leagueId") Long leagueId);

	/** Cuántos miembros han confirmado ya su alineación para esa jornada. */
	@Query("select count(m) from LeagueMember m where m.league.id = :leagueId and m.lineupConfirmedMatchday = :jornada")
	long contarConfirmados(@Param("leagueId") Long leagueId, @Param("jornada") int jornada);

	/** Nombres de quienes todavía no han confirmado, para poder decir en la liga a quién se está esperando. */
	@Query("""
			select m.user.username from LeagueMember m
			where m.league.id = :leagueId and m.lineupConfirmedMatchday <> :jornada
			order by m.user.username
			""")
	List<String> sinConfirmar(@Param("leagueId") Long leagueId, @Param("jornada") int jornada);
}
