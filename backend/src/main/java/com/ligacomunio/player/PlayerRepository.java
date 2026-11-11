package com.ligacomunio.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	List<Player> findByTeamId(Long teamId);

	List<Player> findByPosition(Position position);

	Optional<Player> findByExternalId(Long externalId);

	/** Lista con equipo ya cargado (open-in-view está desactivado). Los filtros nulos se ignoran. */
	@Query("""
			select p from Player p join fetch p.team t
			where (:teamId is null or t.id = :teamId)
			  and (:position is null or p.position = :position)
			order by p.name
			""")
	List<Player> buscar(@Param("teamId") Long teamId, @Param("position") Position position);

	@Query("select p from Player p join fetch p.team where p.id = :id")
	Optional<Player> buscarPorId(@Param("id") Long id);

	/**
	 * Jugadores con valor de mercado de una posición que no están en la lista de excluidos.
	 * La colección no puede estar vacía (el llamador incluye siempre un id centinela).
	 */
	@Query("select p from Player p join fetch p.team where p.position = :position and p.marketValue > 0 and p.id not in :excluidos")
	List<Player> libres(@Param("position") Position position, @Param("excluidos") Collection<Long> excluidos);

	@Query("select p from Player p join fetch p.team where p.marketValue > 0 and p.id not in :excluidos")
	List<Player> libresDeCualquierPosicion(@Param("excluidos") Collection<Long> excluidos);

	/** Jugadores que seguirían fuera del juego por no tener valor de mercado. */
	@Query("select p from Player p where p.marketValue <= 0")
	List<Player> sinValor();

	/** Catálogo de una temporada por equipo, con el equipo cargado (lo usa la simulación). */
	@Query("select p from Player p join fetch p.team t where t.id in :equipos")
	List<Player> deEquipos(@Param("equipos") Collection<Long> equipos);
}
