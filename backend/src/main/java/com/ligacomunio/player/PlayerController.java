package com.ligacomunio.player;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

	private final PlayerRepository playerRepository;

	public PlayerController(PlayerRepository playerRepository) {
		this.playerRepository = playerRepository;
	}

	@GetMapping
	public List<PlayerDto> listar(@RequestParam(required = false) Long teamId,
	                              @RequestParam(required = false) Position position) {
		return playerRepository.buscar(teamId, position).stream().map(PlayerDto::from).toList();
	}

	@GetMapping("/{id}")
	public PlayerDto obtener(@PathVariable Long id) {
		return playerRepository.buscarPorId(id)
				.map(PlayerDto::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jugador no encontrado"));
	}
}
