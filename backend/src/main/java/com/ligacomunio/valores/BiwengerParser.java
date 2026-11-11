package com.ligacomunio.valores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligacomunio.player.PlayerStatus;
import com.ligacomunio.player.Position;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee el JSON de <code>.../competitions/la-liga/data</code> de Biwenger guardado desde el navegador. Solo se
 * usan {@code data.players} (mapa id -> jugador) y {@code data.teams}. El valor de mercado es {@code price};
 * {@code fantasyPrice} es el precio del modo "fantasy" y no se usa. Los entrenadores (posición 5) se descartan.
 */
@Component
public class BiwengerParser {

	private final ObjectMapper mapper;

	public BiwengerParser(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public List<ValorExterno> leer(InputStream contenido) {
		JsonNode raiz;
		try {
			raiz = mapper.readTree(contenido);
		} catch (IOException e) {
			throw formatoIncorrecto("El archivo no es un JSON válido.");
		}
		JsonNode jugadores = raiz.path("data").path("players");
		JsonNode equipos = raiz.path("data").path("teams");
		if (!jugadores.isObject() || !equipos.isObject()) {
			throw formatoIncorrecto("No encuentro data.players y data.teams. ¿Es el archivo de Biwenger?");
		}
		List<ValorExterno> resultado = new ArrayList<>();
		for (JsonNode j : jugadores) {
			Position posicion = posicion(j.path("position").asInt(0));
			long valor = j.path("price").asLong(0);
			String equipo = equipos.path(j.path("teamID").asText()).path("name").asText("");
			String nombre = j.path("name").asText("");
			if (posicion == null || valor <= 0 || nombre.isBlank()) {
				continue;
			}
			resultado.add(new ValorExterno(nombre, j.path("slug").asText(""), equipo, posicion, valor,
					estado(j.path("status").asText("ok"))));
		}
		return resultado;
	}

	/** "warned" (apercibido) sigue pudiendo jugar, así que cuenta como disponible. */
	private static PlayerStatus estado(String estado) {
		return switch (estado) {
			case "injured" -> PlayerStatus.LESIONADO;
			case "sanctioned" -> PlayerStatus.SANCIONADO;
			case "doubt" -> PlayerStatus.DUDA;
			default -> PlayerStatus.DISPONIBLE;
		};
	}

	private static Position posicion(int codigo) {
		return switch (codigo) {
			case 1 -> Position.PORTERO;
			case 2 -> Position.DEFENSA;
			case 3 -> Position.CENTROCAMPISTA;
			case 4 -> Position.DELANTERO;
			default -> null;
		};
	}

	private static ResponseStatusException formatoIncorrecto(String motivo) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, motivo);
	}
}
