package com.ligacomunio.league;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

	Optional<Offer> findByIdAndLeagueId(Long id, Long leagueId);

	/** Pujas al mercado pendientes, de la más alta a la más baja; a igual importe gana la primera en llegar. */
	@Query("""
			select o from Offer o join fetch o.bidder b join fetch o.player p
			where o.league.id = :leagueId and o.seller is null and o.status = :estado
			order by o.amount desc, o.createdAt asc, o.id asc
			""")
	List<Offer> pujasDeMercado(@Param("leagueId") Long leagueId, @Param("estado") OfferStatus estado);

	@Query("select o from Offer o where o.bidder.id = :memberId and o.seller is null and o.status = :estado")
	List<Offer> pujasDelMiembro(@Param("memberId") Long memberId, @Param("estado") OfferStatus estado);

	@Query("select o from Offer o where o.league.id = :leagueId and o.seller is not null and o.status = :estado")
	List<Offer> directasDeLiga(@Param("leagueId") Long leagueId, @Param("estado") OfferStatus estado);

	@Query("select o from Offer o where o.bidder.id = :bidderId and o.player.id = :playerId and o.status = :estado")
	Optional<Offer> delPostor(@Param("bidderId") Long bidderId, @Param("playerId") Long playerId, @Param("estado") OfferStatus estado);

	@Query("select o from Offer o where o.league.id = :leagueId and o.player.id = :playerId and o.status = :estado")
	List<Offer> delJugador(@Param("leagueId") Long leagueId, @Param("playerId") Long playerId, @Param("estado") OfferStatus estado);

	@Query("""
			select o from Offer o join fetch o.player p join fetch p.team join fetch o.bidder b join fetch b.user
			left join fetch o.seller s left join fetch s.user
			where o.bidder.id = :memberId or o.seller.id = :memberId
			order by o.createdAt desc, o.id desc
			""")
	List<Offer> delMiembro(@Param("memberId") Long memberId, Pageable limite);
}
