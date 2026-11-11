package com.ligacomunio.match;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
@AutoConfigureMockMvc(addFilters = false)
class MatchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MatchService matchService;

	@Test
	void creaPartidoYDevuelve201() throws Exception {
		when(matchService.crear(any())).thenReturn(new MatchDto(1L, 1, Instant.parse("2026-08-16T19:00:00Z"),
				4L, "FC Barcelona", 16L, "Real Madrid", null, null, MatchStatus.PROGRAMADO));

		mockMvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content("""
						{"matchday": 1, "kickoff": "2026-08-16T19:00:00Z", "homeTeamId": 4, "awayTeamId": 16}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.homeTeamName").value("FC Barcelona"));
	}

	@Test
	void jornadaCeroEsInvalida() throws Exception {
		mockMvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content("""
						{"matchday": 0, "kickoff": "2026-08-16T19:00:00Z", "homeTeamId": 4, "awayTeamId": 16}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void estadisticasConMinutosFueraDeRangoSonInvalidas() throws Exception {
		mockMvc.perform(put("/api/matches/1/stats").contentType(MediaType.APPLICATION_JSON).content("""
						{"playerId": 7, "minutes": 500}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void estadisticasSinJugadorSonInvalidas() throws Exception {
		mockMvc.perform(put("/api/matches/1/stats").contentType(MediaType.APPLICATION_JSON).content("""
						{"minutes": 90}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void guardaEstadisticasYDevuelveLosPuntos() throws Exception {
		when(matchService.guardarEstadisticas(eq(1L), any())).thenReturn(new EstadisticasDto(7L, "Lamine Yamal",
				com.ligacomunio.player.Position.DELANTERO, 90, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new BigDecimal("5.00")));

		mockMvc.perform(put("/api/matches/1/stats").contentType(MediaType.APPLICATION_JSON).content("""
						{"playerId": 7, "minutes": 90, "goals": 1}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points").value(5.0));
	}
}
