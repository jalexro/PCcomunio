package com.ligacomunio.team;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

	private final TeamRepository teamRepository;

	public TeamController(TeamRepository teamRepository) {
		this.teamRepository = teamRepository;
	}

	@GetMapping
	public List<TeamDto> listar() {
		return teamRepository.findAll(Sort.by("name")).stream().map(TeamDto::from).toList();
	}

	@GetMapping("/{id}")
	public TeamDto obtener(@PathVariable Long id) {
		return teamRepository.findById(id)
				.map(TeamDto::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
	}
}
