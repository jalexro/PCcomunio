package com.ligacomunio.league;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {

	/** Solo el id: la liga se vuelve a leer ya bloqueada, para no trabajar con un estado desactualizado. */
	@Query("select l.id from League l where l.joinCode = :codigo")
	Optional<Long> idPorCodigo(@Param("codigo") String codigo);

	boolean existsByJoinCode(String joinCode);

	/**
	 * Bloquea la fila de la liga hasta el final de la transacción. Toda operación que escribe sobre una liga
	 * (pujar, aceptar, vender, avanzar...) pasa por aquí para evitar carreras sobre presupuestos y plantillas.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from League l where l.id = :id")
	Optional<League> buscarConBloqueo(@Param("id") Long id);

	@Query("select m.league from LeagueMember m where m.user.id = :userId order by m.league.createdAt desc")
	List<League> deUsuario(@Param("userId") Long userId);

	@Query("select l.id from League l where l.status = :estado and l.nextAdvanceAt <= :ahora")
	List<Long> idsVencidas(@Param("estado") LeagueStatus estado, @Param("ahora") Instant ahora);

	/**
	 * Ligas que el reloj tiene que revisar: las que han vencido y, además, todas las que juegan por
	 * confirmación (esas pueden tener que avanzar antes del plazo, en cuanto confirme el último miembro;
	 * quién ha confirmado se comprueba después, ya con la liga bloqueada).
	 */
	@Query("""
			select l.id from League l
			where l.status = :estado and (l.nextAdvanceAt <= :ahora or l.advanceMode = :porConfirmacion)
			order by l.id
			""")
	List<Long> idsAvanzables(@Param("estado") LeagueStatus estado, @Param("ahora") Instant ahora,
	                         @Param("porConfirmacion") AdvanceMode porConfirmacion);
}
