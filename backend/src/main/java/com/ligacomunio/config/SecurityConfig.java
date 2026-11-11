package com.ligacomunio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * API sin estado: el cliente manda "Authorization: Bearer <access token>". Sin cookies no hay CSRF que proteger.
 * CORS se toma de {@link com.ligacomunio.CorsConfig}.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/refresh", "/api/auth/logout",
								// Se usan sin sesión: el usuario llega desde un enlace del correo
								// o todavía no puede entrar porque ha perdido la contraseña.
								"/api/auth/verify", "/api/auth/verify/resend",
								"/api/auth/password/forgot", "/api/auth/password/reset").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						// Spring reenvía los errores a /error; si estuviera protegido, un 400 o un 404 llegaría como 401.
						.requestMatchers("/error").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertidorJwt())))
				.headers(h -> h
						// La API solo devuelve JSON: nada que incrustar ni que mostrar en un marco.
						.frameOptions(f -> f.deny())
						.contentTypeOptions(Customizer.withDefaults())
						.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
						// Un año de HSTS, solo efectivo cuando se sirva por HTTPS (en http se ignora).
						.httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)));
		return http.build();
	}

	/** El claim "role" del token ("USER"/"ADMIN") pasa a la autoridad ROLE_USER / ROLE_ADMIN. */
	private JwtAuthenticationConverter convertidorJwt() {
		JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
		autoridades.setAuthoritiesClaimName("role");
		autoridades.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
		return convertidor;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
