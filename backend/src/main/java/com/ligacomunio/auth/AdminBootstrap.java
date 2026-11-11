package com.ligacomunio.auth;

import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.Role;
import com.ligacomunio.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Crea el primer ADMIN al arrancar si ADMIN_EMAIL y ADMIN_PASSWORD están definidos y ese usuario no existe. */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;

	public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
	                      @Value("${app.admin.email:}") String email,
	                      @Value("${app.admin.password:}") String password) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email.trim().toLowerCase(Locale.ROOT);
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isEmpty() || password.isEmpty()) {
			return;
		}
		if (password.length() < 8 || password.length() > 72) {
			log.warn("ADMIN_PASSWORD debe tener entre 8 y 72 caracteres: no se crea el administrador");
			return;
		}
		if (userRepository.existsByEmail(email)) {
			return;
		}
		AppUser admin = new AppUser();
		admin.setEmail(email);
		admin.setUsername(nombreLibre(email));
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setRole(Role.ADMIN);
		// Lo crea el operador desde el .env, no hay a quién mandarle el enlace de confirmación.
		admin.setEmailVerified(true);
		userRepository.save(admin);
		log.info("Administrador creado: {}", email);
	}

	/** Deriva un nombre de usuario válido (3-20 caracteres) de la parte local del email. */
	private String nombreLibre(String email) {
		String base = email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length())
				.replaceAll("[^A-Za-z0-9_]", "_");
		base = (base + "___").substring(0, Math.min(Math.max(base.length(), 3), 18));
		String candidato = base;
		for (int i = 2; userRepository.existsByUsernameIgnoreCase(candidato); i++) {
			candidato = base + i;
		}
		return candidato;
	}
}
