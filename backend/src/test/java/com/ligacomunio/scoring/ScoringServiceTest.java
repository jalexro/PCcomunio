package com.ligacomunio.scoring;

import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.player.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

	private final ScoringService servicio = new ScoringService();

	@Test
	void delanteroConGolAsistenciaYBuenosPases() {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setMinutes(90);
		s.setGoals(1);
		s.setAssists(1);
		s.setTacklesWon(2);
		s.setPassesCompleted(25);
		s.setPassesFailed(5); // 83,33 %
		// 2 (minutos) + 3 (gol) + 2 (asistencia) + 2 * 0,25 (entradas) + 0,5 * (83,33 - 75) / 10 = 7,92
		assertThat(servicio.calcular(Position.DELANTERO, s)).isEqualByComparingTo(new BigDecimal("7.92"));
	}

	@Test
	void delanteroRestaPorTirosFallados() {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setMinutes(90);
		s.setTirosFallados(4);
		// 2 (minutos) - 4 * 0,25
		assertThat(servicio.calcular(Position.DELANTERO, s)).isEqualByComparingTo(new BigDecimal("1.00"));
	}

	@Test
	void porteroConPorteriaACero() {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setMinutes(90);
		s.setSaves(4);
		s.setShotsFaced(4);
		// 2 (minutos) + 4 * 0,75 (paradas) + 2 (portería a cero) + (100 - 70) / 10
		assertThat(servicio.calcular(Position.PORTERO, s)).isEqualByComparingTo(new BigDecimal("10.00"));
	}

	@Test
	void tarjetasRestan() {
		PlayerMatchStats s = new PlayerMatchStats();
		s.setMinutes(30);
		s.setYellowCards(1);
		s.setRedCards(1);
		// 1 (minutos) - 1 - 3
		assertThat(servicio.calcular(Position.DEFENSA, s)).isEqualByComparingTo(new BigDecimal("-3.00"));
	}

	@Test
	void sinMinutosNoPuntua() {
		assertThat(servicio.calcular(Position.CENTROCAMPISTA, new PlayerMatchStats())).isEqualByComparingTo(BigDecimal.ZERO);
	}
}
