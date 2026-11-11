package com.ligacomunio.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

	List<Match> findByMatchdayOrderByKickoff(int matchday);

	/** Lista con ambos equipos cargados. Si matchday es nulo, devuelve todos los partidos. */
	@Query("""
			select m from Match m join fetch m.homeTeam join fetch m.awayTeam
			where (:matchday is null or m.matchday = :matchday)
			order by m.matchday, m.kickoff
			""")
	List<Match> buscar(@Param("matchday") Integer matchday);

	Optional<Match> findByExternalId(Long externalId);

	long countBySeasonAndMatchday(int season, int matchday);

	long countBySeasonAndMatchdayAndStatusNot(int season, int matchday, MatchStatus status);

	/** Última jornada del calendario de una temporada, o null si no hay partidos. */
	@Query("select max(m.matchday) from Match m where m.season = :season")
	Integer ultimaJornada(@Param("season") int season);

	/** Partidos finalizados de una jornada de los que aún no hay ninguna estadística importada. */
	@Query("""
			select m from Match m
			where m.season = :season and m.matchday = :jornada and m.status = :estado
			  and m.externalId is not null
			  and not exists (select 1 from PlayerMatchStats s where s.match = m)
			order by m.kickoff
			""")
	List<Match> pendientesDeEstadisticas(@Param("season") int season, @Param("jornada") int jornada,
	                                     @Param("estado") MatchStatus estado);

	/**
	 * Partidos de una jornada que todavía no tienen ninguna estadística, estén programados o finalizados.
	 * Es lo que la simulación tiene que rellenar; los que ya las tienen (importados de verdad) se respetan.
	 */
	@Query("""
			select m.id from Match m
			where m.season = :season and m.matchday = :jornada
			  and not exists (select 1 from PlayerMatchStats s where s.match = m)
			order by m.kickoff, m.id
			""")
	List<Long> idsSinEstadisticas(@Param("season") int season, @Param("jornada") int jornada);

	@Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam where m.id = :id")
	Optional<Match> buscarPorId(@Param("id") Long id);
}
