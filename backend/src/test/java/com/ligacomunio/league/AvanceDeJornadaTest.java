package com.ligacomunio.league;

import com.ligacomunio.simulacion.SimuladorDeJornada;
import com.ligacomunio.simulacion.SimuladorDeJornada.ResultadoSimulacion;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvanceDeJornadaTest {

	private final LeagueRepository ligas = mock(LeagueRepository.class);
	private final MatchdayService jornadas = mock(MatchdayService.class);
	private final SimuladorDeJornada simulador = mock(SimuladorDeJornada.class);
	private final AvanceDeJornada avance = new AvanceDeJornada(ligas, jornadas, simulador);

	private League liga(int jornadaActual, LeagueStatus estado) {
		League l = new League();
		l.setSeason(2024);
		l.setCurrentMatchday(jornadaActual);
		l.setStatus(estado);
		when(ligas.findById(7L)).thenReturn(Optional.of(l));
		return l;
	}

	@Test
	void simulaLaJornadaSiguienteSiLeFaltanEstadisticasYDespuesAvanza() {
		liga(2, LeagueStatus.EN_CURSO);
		when(jornadas.jornadaLista(2024, 3)).thenReturn(false);
		when(simulador.simularJornada(2024, 3)).thenReturn(new ResultadoSimulacion(10, 300));

		avance.avanzar(7L, false);

		verify(simulador).simularJornada(2024, 3);
		verify(jornadas).avanzar(7L, false);
	}

	@Test
	void noSimulaNadaSiLaJornadaYaTieneSusEstadisticas() {
		liga(2, LeagueStatus.EN_CURSO);
		when(jornadas.jornadaLista(2024, 3)).thenReturn(true);

		avance.avanzar(7L, false);

		verify(simulador, never()).simularJornada(2024, 3);
		verify(jornadas).avanzar(7L, false);
	}

	/** El reloj pasa por todas las ligas: si a una no le toca, no se le simula la jornada por adelantado. */
	@Test
	void conAvanceAutomaticoNoSimulaNiAvanzaSiTodaviaNoToca() {
		League liga = liga(2, LeagueStatus.EN_CURSO);
		when(jornadas.tocaAvanzar(liga)).thenReturn(false);

		avance.avanzar(7L, true);

		verify(simulador, never()).simularJornada(2024, 3);
		verify(jornadas, never()).avanzar(7L, true);
	}

	@Test
	void conAvanceAutomaticoSimulaYAvanzaCuandoYaToca() {
		League liga = liga(2, LeagueStatus.EN_CURSO);
		when(jornadas.tocaAvanzar(liga)).thenReturn(true);
		when(jornadas.jornadaLista(2024, 3)).thenReturn(false);
		when(simulador.simularJornada(2024, 3)).thenReturn(new ResultadoSimulacion(10, 300));

		avance.avanzar(7L, true);

		verify(simulador).simularJornada(2024, 3);
		verify(jornadas).avanzar(7L, true);
	}

	@Test
	void unaLigaTerminadaNoSimulaNada() {
		liga(38, LeagueStatus.FINALIZADA);

		avance.avanzar(7L, true);

		verify(simulador, never()).simularJornada(2024, 39);
	}
}
