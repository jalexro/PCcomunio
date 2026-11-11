package com.ligacomunio.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerMatchStatsRepository extends JpaRepository<PlayerMatchStats, Long> {

	List<PlayerMatchStats> findByMatchId(Long matchId);

	boolean existsByMatchId(Long matchId);

	List<PlayerMatchStats> findByPlayerId(Long playerId);

	Optional<PlayerMatchStats> findByPlayerIdAndMatchId(Long playerId, Long matchId);

	/** Puntos de todos los jugadores en una jornada de una temporada: filas [playerId, points]. */
	@Query("select s.player.id, s.points from PlayerMatchStats s where s.match.season = :season and s.match.matchday = :matchday")
	List<Object[]> puntosDeLaJornada(@Param("season") int season, @Param("matchday") int matchday);

	/** Estadísticas de un partido con el jugador cargado, ordenadas por puntos descendente. */
	@Query("""
			select s from PlayerMatchStats s join fetch s.player
			where s.match.id = :matchId
			order by s.points desc, s.player.name
			""")
	List<PlayerMatchStats> buscarPorPartido(@Param("matchId") Long matchId);
}
