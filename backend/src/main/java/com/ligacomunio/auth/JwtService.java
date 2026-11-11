package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtService {

	private final JwtEncoder encoder;
	private final long accessTtlSegundos;

	public JwtService(JwtEncoder encoder, @Value("${app.jwt.access-ttl-seconds:900}") long accessTtlSegundos) {
		this.encoder = encoder;
		this.accessTtlSegundos = accessTtlSegundos;
	}

	public String crearAccessToken(AppUser usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(usuario.getId()))
				.issuedAt(ahora)
				.expiresAt(ahora.plusSeconds(accessTtlSegundos))
				.claim("role", usuario.getRole().name())
				.build();
		JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
	}

	public long accessTtlSegundos() {
		return accessTtlSegundos;
	}
}
