package com.ligacomunio.importacion;

import com.ligacomunio.importacion.GuardadoImportacion.Guardado;
import com.ligacomunio.match.Match;
import com.ligacomunio.match.MatchRepository;
import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.match.PlayerMatchStatsRepository;
import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import com.ligacomunio.player.Position;
import com.ligacomunio.scoring.ScoringService;
import com.ligacomunio.team.Team;
import com.ligacomunio.team.TeamRepository;
import com.ligacomunio.valores.GeneradorDeValores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardadoImportacionTest {

	@Mock
	private TeamRepository teamRepository;
	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private MatchRepository matchRepository;
	@Mock
	private PlayerMatchStatsRepository statsRepository;

	private GuardadoImportacion guardado;
	private Match partido;
	private Team equipo;

	@BeforeEach
	void preparar() {
		guardado = new GuardadoImportacion(teamRepository, playerRepository, matchRepository, statsRepository, new ScoringService(),
				new GeneradorDeValores());
		partido = new Match();
		ReflectionTestUtils.setField(partido, "id", 1L);
		equipo = new Team();
		ReflectionTestUtils.setField(equipo, "id", 10L);
		equipo.setExternalId(531L);
	}

	private EstadisticaExterna delantero(long jugadorId, int minutos, int goles) {
		return new EstadisticaExterna(jugadorId, "Delantero " + jugadorId, null, 531L, Position.DELANTERO,
				minutos, goles, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
	}

	@Test
	void creaElJugadorQueFaltaYCalculaSusPuntos() {
		when(matchRepository.findById(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.findByExternalId(200L)).thenReturn(Optional.empty());
		when(teamRepository.findByExternalId(531L)).thenReturn(Optional.of(equipo));
		when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
			Player p = inv.getArgument(0);
			ReflectionTestUtils.setField(p, "id", 50L);
			return p;
		});
		when(statsRepository.findByPlayerIdAndMatchId(50L, 1L)).thenReturn(Optional.empty());

		Guardado resultado = guardado.guardarEstadisticas(1L, List.of(delantero(200L, 90, 1)));

		assertThat(resultado).isEqualTo(new Guardado(1, 0));
		ArgumentCaptor<Player> jugador = ArgumentCaptor.forClass(Player.class);
		verify(playerRepository).save(jugador.capture());
		assertThat(jugador.getValue().getExternalId()).isEqualTo(200L);
		assertThat(jugador.getValue().getPosition()).isEqualTo(Position.DELANTERO);

		ArgumentCaptor<PlayerMatchStats> stats = ArgumentCaptor.forClass(PlayerMatchStats.class);
		verify(statsRepository).save(stats.capture());
		// 2 (minutos) + 3 (gol de delantero)
		assertThat(stats.getValue().getPoints()).isEqualByComparingTo(new BigDecimal("5.00"));
	}

	@Test
	void omiteALosQueNoJugaronYNoCreaSuJugador() {
		when(matchRepository.findById(1L)).thenReturn(Optional.of(partido));

		Guardado resultado = guardado.guardarEstadisticas(1L, List.of(delantero(300L, 0, 0)));

		assertThat(resultado).isEqualTo(new Guardado(0, 1));
		verify(playerRepository, never()).save(any());
		verify(statsRepository, never()).save(any());
	}

	@Test
	void omiteAlJugadorSiSuEquipoNoEstaImportado() {
		when(matchRepository.findById(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.findByExternalId(200L)).thenReturn(Optional.empty());
		when(teamRepository.findByExternalId(531L)).thenReturn(Optional.empty());

		Guardado resultado = guardado.guardarEstadisticas(1L, List.of(delantero(200L, 90, 1)));

		assertThat(resultado).isEqualTo(new Guardado(0, 1));
		verify(statsRepository, never()).save(any());
	}

	@Test
	void reimportarReemplazaLasEstadisticasSinDuplicar() {
		Player existente = new Player();
		ReflectionTestUtils.setField(existente, "id", 50L);
		existente.setPosition(Position.DELANTERO);
		PlayerMatchStats previas = new PlayerMatchStats();
		previas.setGoals(9);
		when(matchRepository.findById(1L)).thenReturn(Optional.of(partido));
		when(playerRepository.findByExternalId(200L)).thenReturn(Optional.of(existente));
		when(statsRepository.findByPlayerIdAndMatchId(50L, 1L)).thenReturn(Optional.of(previas));

		guardado.guardarEstadisticas(1L, List.of(delantero(200L, 90, 1)));

		ArgumentCaptor<PlayerMatchStats> stats = ArgumentCaptor.forClass(PlayerMatchStats.class);
		verify(statsRepository).save(stats.capture());
		assertThat(stats.getValue()).isSameAs(previas);
		assertThat(stats.getValue().getGoals()).isEqualTo(1);
		verify(playerRepository, never()).save(any());
	}
}
