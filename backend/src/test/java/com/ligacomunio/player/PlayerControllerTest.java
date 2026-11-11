package com.ligacomunio.player;

import com.ligacomunio.team.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlayerController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlayerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PlayerRepository playerRepository;

	private Player jugador() {
		Team equipo = new Team();
		ReflectionTestUtils.setField(equipo, "id", 4L);
		equipo.setName("FC Barcelona");
		equipo.setShortName("BAR");

		Player p = new Player();
		ReflectionTestUtils.setField(p, "id", 7L);
		p.setName("Pedri");
		p.setPosition(Position.CENTROCAMPISTA);
		p.setTeam(equipo);
		p.setMarketValue(new BigDecimal("90000000"));
		return p;
	}

	@Test
	void listaJugadoresSinFiltros() throws Exception {
		when(playerRepository.buscar(isNull(), isNull())).thenReturn(List.of(jugador()));

		mockMvc.perform(get("/api/players"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Pedri"))
				.andExpect(jsonPath("$[0].position").value("CENTROCAMPISTA"))
				.andExpect(jsonPath("$[0].teamShortName").value("BAR"));
	}

	@Test
	void filtraPorEquipoYPosicion() throws Exception {
		when(playerRepository.buscar(4L, Position.CENTROCAMPISTA)).thenReturn(List.of(jugador()));

		mockMvc.perform(get("/api/players").param("teamId", "4").param("position", "CENTROCAMPISTA"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void posicionInvalidaDevuelve400() throws Exception {
		mockMvc.perform(get("/api/players").param("position", "ENTRENADOR"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void jugadorInexistenteDevuelve404() throws Exception {
		when(playerRepository.buscarPorId(99L)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/players/99"))
				.andExpect(status().isNotFound());
	}
}
