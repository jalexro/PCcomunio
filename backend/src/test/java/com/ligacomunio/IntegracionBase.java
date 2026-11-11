package com.ligacomunio;

import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.match.MatchStatus;
import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.player.Position;
import com.ligacomunio.team.Team;
import com.ligacomunio.team.TeamRepository;
import com.ligacomunio.user.AppUser;
import com.ligacomunio.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de los tests de integración: aplicación completa contra un PostgreSQL real en un contenedor (con las
 * migraciones de Flyway). Se omiten solos si Docker no está disponible.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
// Cada clase arranca su propio contenedor: si el contexto de Spring se reutilizara entre clases, seguiría
// apuntando al contenedor ya parado de la clase anterior y las conexiones se quedarían esperando.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
		"app.jwt.secret=una-clave-de-prueba-de-32-bytes-minimo!",
		"app.league.scheduler-delay-ms=3600000"
})
public abstract class IntegracionBase {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected UserRepository userRepository;
	@Autowired
	protected TeamRepository teamRepository;
	@Autowired
	protected PlayerRepository playerRepository;
	@Autowired
	protected MatchRepository matchRepository;
	@Autowired
	protected PlayerMatchStatsRepository statsRepository;

	protected Team equipoA;
	protected Team equipoB;
	protected final List<Player> catalogo = new ArrayList<>();

	@BeforeEach
	void limpiarBaseDeDatos() {
		jdbc.execute("TRUNCATE offer, market_listing, squad_player, member_matchday_score, league_member, league, "
				+ "player_match_stats, matches, player, team, refresh_token, app_user RESTART IDENTITY CASCADE");
		catalogo.clear();
		equipoA = equipo("Equipo A", 1L);
		equipoB = equipo("Equipo B", 2L);
		// Suficientes jugadores con valor para tres plantillas iniciales (1/4/4/2 cada una) aunque el mercado
		// aleatorio de 10 se lleve a casi todos los de una misma posición
		crearJugadores(Position.PORTERO, 12);
		crearJugadores(Position.DEFENSA, 26);
		crearJugadores(Position.CENTROCAMPISTA, 26);
		crearJugadores(Position.DELANTERO, 18);
	}

	protected AppUser usuario(String nombre) {
		AppUser u = new AppUser();
		u.setEmail(nombre + "@ejemplo.test");
		u.setUsername(nombre);
		u.setPasswordHash("no-se-usa-en-estos-tests");
		return userRepository.save(u);
	}

	protected Team equipo(String nombre, Long externalId) {
		Team t = new Team();
		t.setName(nombre);
		t.setShortName(nombre.substring(0, 3).toUpperCase());
		t.setExternalId(externalId);
		return teamRepository.save(t);
	}

	protected Player jugador(String nombre, Position posicion, Team equipo, long valor) {
		Player p = new Player();
		p.setName(nombre);
		p.setPosition(posicion);
		p.setTeam(equipo);
		p.setMarketValue(BigDecimal.valueOf(valor));
		return playerRepository.save(p);
	}

	private void crearJugadores(Position posicion, int cuantos) {
		for (int i = 1; i <= cuantos; i++) {
			catalogo.add(jugador(posicion.name().substring(0, 3) + " " + i, posicion, i % 2 == 0 ? equipoA : equipoB,
					1_000_000L * (i + 4)));
		}
	}

	protected Match partido(int temporada, int jornada, MatchStatus estado) {
		Match m = new Match();
		m.setSeason(temporada);
		m.setMatchday(jornada);
		m.setKickoff(Instant.parse("2024-08-15T19:00:00Z").plusSeconds(jornada * 604800L));
		m.setHomeTeam(equipoA);
		m.setAwayTeam(equipoB);
		m.setStatus(estado);
		m.setExternalId(1000L + jornada);
		return matchRepository.save(m);
	}

	protected void estadistica(Player jugador, Match partido, String puntos) {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setPlayer(jugador);
		s.setMatch(partido);
		s.setMinutes(90);
		s.setPoints(new BigDecimal(puntos));
		statsRepository.save(s);
	}

	/** Da la misma puntuación a todos los jugadores del catálogo en un partido. */
	protected void estadisticasParaTodos(Match partido, String puntos) {
		catalogo.forEach(j -> estadistica(j, partido, puntos));
	}

	protected BigDecimal presupuesto(Long memberId) {
		return jdbc.queryForObject("select budget from league_member where id = ?", BigDecimal.class, memberId);
	}

	protected void fijarPresupuesto(Long memberId, long euros) {
		jdbc.update("update league_member set budget = ? where id = ?", BigDecimal.valueOf(euros), memberId);
	}
}
