package com.ligacomunio.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

	/** Con el usuario cargado: open-in-view está desactivado. */
	@Query("select t from UserToken t join fetch t.user where t.tokenHash = :hash and t.type = :tipo")
	Optional<UserToken> buscar(@Param("hash") String hash, @Param("tipo") TokenType tipo);

	/** Al emitir uno nuevo se invalidan los anteriores del mismo tipo: solo vale el último enviado. */
	@Modifying
	@Query("update UserToken t set t.usedAt = :ahora where t.user.id = :userId and t.type = :tipo and t.usedAt is null")
	int gastarAnteriores(@Param("userId") Long userId, @Param("tipo") TokenType tipo, @Param("ahora") Instant ahora);

	@Modifying
	@Query("delete from UserToken t where t.expiresAt < :limite")
	int borrarCaducados(@Param("limite") Instant limite);
}
