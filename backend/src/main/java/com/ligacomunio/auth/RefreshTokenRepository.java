package com.ligacomunio.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/** Con el usuario cargado: open-in-view está desactivado. */
	@Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
	Optional<RefreshToken> buscarPorHash(@Param("hash") String hash);

	@Modifying
	@Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
	int revocarTodosDelUsuario(@Param("userId") Long userId);

	@Modifying
	@Query("delete from RefreshToken t where t.expiresAt < :limite")
	int borrarCaducados(@Param("limite") Instant limite);
}
