package com.ligacomunio.league;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface MarketListingRepository extends JpaRepository<MarketListing, Long> {

	@Query("select l from MarketListing l join fetch l.player p join fetch p.team where l.league.id = :leagueId and l.cycle = :cycle")
	List<MarketListing> delCiclo(@Param("leagueId") Long leagueId, @Param("cycle") int cycle);

	@Query("select l.player.id from MarketListing l where l.league.id = :leagueId and l.cycle = :cycle")
	Set<Long> idsDelCiclo(@Param("leagueId") Long leagueId, @Param("cycle") int cycle);

	boolean existsByLeagueIdAndPlayerIdAndCycle(Long leagueId, Long playerId, int cycle);

	@Modifying(flushAutomatically = true)
	@Query("delete from MarketListing l where l.league.id = :leagueId")
	void borrarDeLiga(@Param("leagueId") Long leagueId);
}
