package com.ligacomunio.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TeamRepository teamRepository;

	private Team equipo() {
		Team t = new Team();
		ReflectionTestUtils.setField(t, "id", 16L);
		t.setName("Real Madrid");
		t.setShortName("RMA");
		return t;
	}

	@Test
	void listaEquipos() throws Exception {
		when(teamRepository.findAll(Sort.by("name"))).thenReturn(List.of(equipo()));

		mockMvc.perform(get("/api/teams"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Real Madrid"))
				.andExpect(jsonPath("$[0].shortName").value("RMA"));
	}

	@Test
	void equipoInexistenteDevuelve404() throws Exception {
		when(teamRepository.findById(99L)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/teams/99"))
				.andExpect(status().isNotFound());
	}
}
