package com.ligacomunio.scoring;

import com.ligacomunio.match.PlayerMatchStats;
import com.ligacomunio.player.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de puntuación por posición. Todos los pesos están aquí para ajustarlos en un solo sitio.
 * Los valores son una propuesta inicial, no unas reglas definitivas.
 */
@Service
public class ScoringService {

	// Mínimo de pases / tiros para que el porcentaje cuente (evita premiar o castigar muestras diminutas)
	private static final int MIN_PASES = 10;
	private static final int MIN_TIROS_RECIBIDOS = 3;
	private static final double PORCENTAJE_PASES_BASE = 75.0;
	private static final double PORCENTAJE_PARADAS_BASE = 70.0;

	/** Pesos de los jugadores de campo. */
	private record PesosCampo(double gol, double asistencia, double entradaGanada, double factorPases, double tiroFallado) {}

	private static final PesosCampo DELANTERO = new PesosCampo(3, 2, 0.25, 0.5, 0.25);
	private static final PesosCampo CENTROCAMPISTA = new PesosCampo(4, 3, 0.5, 1.0, 0.20);
	private static final PesosCampo DEFENSA = new PesosCampo(6, 3, 1.0, 0.75, 0.10);

	public BigDecimal calcular(Position posicion, PlayerMatchStats s) {
		double puntos = puntosPorMinutos(s.getMinutes()) - s.getYellowCards() - 3.0 * s.getRedCards();
		puntos += switch (posicion) {
			case PORTERO -> puntosPortero(s);
			case DELANTERO -> puntosJugadorCampo(DELANTERO, s);
			case CENTROCAMPISTA -> puntosJugadorCampo(CENTROCAMPISTA, s);
			case DEFENSA -> puntosJugadorCampo(DEFENSA, s);
		};
		return BigDecimal.valueOf(puntos).setScale(2, RoundingMode.HALF_UP);
	}

	private double puntosPorMinutos(int minutos) {
		if (minutos <= 0) return 0;
		return minutos >= 70 ? 2 : 1;
	}

	private double puntosJugadorCampo(PesosCampo p, PlayerMatchStats s) {
		double puntos = p.gol() * s.getGoals() + p.asistencia() * s.getAssists() + p.entradaGanada() * s.getTacklesWon()
				- p.tiroFallado() * s.getTirosFallados();
		int pases = s.getPassesCompleted() + s.getPassesFailed();
		if (pases >= MIN_PASES) {
			double porcentajeAcierto = 100.0 * s.getPassesCompleted() / pases;
			puntos += p.factorPases() * (porcentajeAcierto - PORCENTAJE_PASES_BASE) / 10.0;
		}
		return puntos;
	}

	private double puntosPortero(PlayerMatchStats s) {
		double puntos = 6.0 * s.getGoals() + 0.75 * s.getSaves() - 2.0 * s.getGoalsConceded();
		if (s.getMinutes() >= 80 && s.getGoalsConceded() == 0) {
			puntos += 2;
		}
		if (s.getShotsFaced() >= MIN_TIROS_RECIBIDOS) {
			double porcentajeParadas = 100.0 * s.getSaves() / s.getShotsFaced();
			puntos += (porcentajeParadas - PORCENTAJE_PARADAS_BASE) / 10.0;
		}
		return puntos;
	}
}
