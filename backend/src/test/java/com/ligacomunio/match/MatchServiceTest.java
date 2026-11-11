package com.ligacomunio.match;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.player.Position;
import com.ligacomunio.scoring.ScoringService;
import com.ligacomunio.team.Team;
import com.ligacomunio.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	@Mock
	private MatchRepository matchRepository;
	@Mock
	private TeamRepository teamRepository;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private PlayerMatchStatsRepository statsRepository;

	private MatchService servicio;
	private Team barcelona;
	private Team madrid;
	private Team sevilla;
	private Match partido;

	@BeforeEach
	void preparar() {
		servicio = new MatchService(matchRepository, teamRepository, playerRepository, statsRepository, new ScoringService());
		barcelona = equipo(4L, "FC Barcelona");
		madrid = equipo(16L, "Real Madrid");
		sevilla = equipo(18L, "Sevilla FC");

		partido = new Match();
		ReflectionTestUtils.setField(partido, "id", 1L);
		partido.setMatchday(1);
		partido.setKickoff(Instant.parse("2026-08-16T19:00:00Z"));
		partido.setHomeTeam(barcelona);
		partido.setAwayTeam(madrid);
	}

	private Team equipo(Long id, String nombre) {
		Team t = new Team();
		ReflectionTestUtils.setField(t, "id", id);
		t.setName(nombre);
		t.setShortName(nombre.substring(0, 3).toUpperCase());
		return t;
	}

	private Player jugador(Long id, Position posicion, Team equipo) {
		Player p = new Player();
		ReflectionTestUtils.setField(p, "id", id);
		p.setName("Jugador " + id);
		p.setPosition(posicion);
		p.setTeam(equipo);
		return p;
	}

	private EstadisticasRequest estadisticas(Long jugadorId, int minutos, int goles) {
		return new EstadisticasRequest(jugadorId, minutos, goles, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	@Test
	void guardaEstadisticasYCalculaPuntos() {
		Player delantero = jugador(7L, Position.DELANTERO, barcelona);
		when(matchRepository.buscarPorId(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.buscarPorId(7L)).thenReturn(Optional.of(delantero));
		when(statsRepository.findByPlayerIdAndMatchId(7L, 1L)).thenReturn(Optional.empty());
		when(statsRepository.save(any(PlayerMatchStats.class))).thenAnswer(inv -> inv.getArgument(0));

		EstadisticasDto resultado = servicio.guardarEstadisticas(1L, estadisticas(7L, 90, 2));

		// 2 (minutos) + 2 goles * 3 (delantero)
		assertThat(resultado.points()).isEqualByComparingTo(new BigDecimal("8.00"));
		assertThat(resultado.goals()).isEqualTo(2);
	}

	@Test
	void reemplazaLasEstadisticasExistentes() {
		Player delantero = jugador(7L, Position.DELANTERO, barcelona);
		PlayerMatchStats existente = new PlayerMatchStats();
		existente.setGoals(5);
		when(matchRepository.buscarPorId(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.buscarPorId(7L)).thenReturn(Optional.of(delantero));
		when(statsRepository.findByPlayerIdAndMatchId(7L, 1L)).thenReturn(Optional.of(existente));
		when(statsRepository.save(any(PlayerMatchStats.class))).thenAnswer(inv -> inv.getArgument(0));

		EstadisticasDto resultado = servicio.guardarEstadisticas(1L, estadisticas(7L, 90, 1));

		assertThat(resultado.goals()).isEqualTo(1);
		assertThat(resultado.points()).isEqualByComparingTo(new BigDecimal("5.00"));
	}

	@Test
	void rechazaJugadorDeUnEquipoAjenoAlPartido() {
		Player ajeno = jugador(9L, Position.DEFENSA, sevilla);
		when(matchRepository.buscarPorId(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.buscarPorId(9L)).thenReturn(Optional.of(ajeno));

		assertThatThrownBy(() -> servicio.guardarEstadisticas(1L, estadisticas(9L, 90, 0)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("no pertenece");
	}

	@Test
	void rechazaPartidoConElMismoEquipoLocalYVisitante() {
		CrearPartidoRequest request = new CrearPartidoRequest(1, Instant.now(), 4L, 4L);

		assertThatThrownBy(() -> servicio.crear(request))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("distintos");
	}

	@Test
	void partidoInexistenteDa404() {
		when(matchRepository.buscarPorId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.obtener(99L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("no encontrado");
	}
}
