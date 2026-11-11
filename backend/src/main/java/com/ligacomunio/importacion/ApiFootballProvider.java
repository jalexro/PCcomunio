package com.ligacomunio.importacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente de API-Football (v3). El plan gratuito permite 100 peticiones al día y 10 por minuto,
 * así que las peticiones se espacian y se corta antes de agotar la cuota diaria.
 */
@Component
public class ApiFootballProvider implements StatsProvider {

	private static final int LALIGA = 140;
	private static final int RESERVA_DIARIA = 3;
	/** Tope de seguridad: si la API dijera un número de páginas absurdo, no se vacía la cuota del día. */
	private static final int MAX_PAGINAS = 40;

	private final RestClient client;
	private final String clave;
	private final long intervaloMs;

	private long ultimaPeticionMs;
	private Integer restantesDia;

	public ApiFootballProvider(RestClient.Builder builder,
	                           @Value("${apifootball.base-url}") String baseUrl,
	                           @Value("${apifootball.key:}") String clave,
	                           @Value("${apifootball.min-interval-ms:6500}") long intervaloMs) {
		this.client = builder.baseUrl(baseUrl).build();
		this.clave = clave.trim();
		this.intervaloMs = intervaloMs;
	}

	@Override
	public List<EquipoExterno> equipos(int temporada) {
		List<EquipoExterno> equipos = new ArrayList<>();
		get("/teams?league=" + LALIGA + "&season=" + temporada).forEach(r -> equipos.add(ApiFootballMapper.equipo(r)));
		return equipos;
	}

	/**
	 * Una petición por página de 20 jugadores (unas 30 para toda LaLiga), así que cabe de sobra en la cuota
	 * diaria del plan gratuito. Se usa /players y no /players/squads porque este último devuelve la plantilla
	 * actual, sin poder pedir una temporada concreta.
	 */
	@Override
	public List<JugadorExterno> plantillas(int temporada) {
		List<JugadorExterno> jugadores = new ArrayList<>();
		int pagina = 1;
		int paginas = 1;
		while (pagina <= paginas && pagina <= MAX_PAGINAS) {
			JsonNode cuerpo = getCompleto("/players?league=" + LALIGA + "&season=" + temporada + "&page=" + pagina);
			paginas = Math.max(1, cuerpo.path("paging").path("total").asInt(1));
			for (JsonNode r : cuerpo.path("response")) {
				JugadorExterno jugador = ApiFootballMapper.jugadorDePlantilla(r);
				if (jugador != null) {
					jugadores.add(jugador);
				}
			}
			pagina++;
		}
		return jugadores;
	}

	@Override
	public List<PartidoExterno> partidos(int temporada) {
		List<PartidoExterno> partidos = new ArrayList<>();
		get("/fixtures?league=" + LALIGA + "&season=" + temporada).forEach(r -> partidos.add(ApiFootballMapper.partido(r)));
		return partidos;
	}

	@Override
	public List<EstadisticaExterna> estadisticasPartido(long partidoExternoId) {
		return ApiFootballMapper.estadisticas(get("/fixtures/players?fixture=" + partidoExternoId));
	}

	@Override
	public synchronized Integer peticionesRestantesHoy() {
		return restantesDia;
	}

	private JsonNode get(String ruta) {
		return getCompleto(ruta).path("response");
	}

	/** Igual que {@link #get} pero devuelve el cuerpo entero, que es donde viene la paginación. */
	private synchronized JsonNode getCompleto(String ruta) {
		if (clave.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta la clave API_FOOTBALL_KEY");
		}
		if (restantesDia == null) {
			sembrarCuota();
		}
		if (restantesDia != null && restantesDia <= RESERVA_DIARIA) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Cuota diaria de API-Football casi agotada (" + restantesDia + " peticiones restantes)");
		}

		ResponseEntity<JsonNode> respuesta = llamar(ruta);
		descontarPeticion(respuesta);

		JsonNode cuerpo = respuesta.getBody();
		if (cuerpo == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta vacía de API-Football");
		}
		// "errors" es [] si todo va bien y un objeto {campo: mensaje} si hay problemas.
		JsonNode errores = cuerpo.path("errors");
		if (errores.size() > 0) {
			HttpStatus estado = errores.has("rateLimit") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
			throw new ResponseStatusException(estado, "API-Football: " + errores);
		}
		return cuerpo;
	}

	/** Lee del endpoint /status cuánta cuota queda hoy (esa consulta no gasta cuota). */
	private void sembrarCuota() {
		JsonNode cuerpo = llamar("/status").getBody();
		JsonNode peticiones = cuerpo == null ? null : cuerpo.path("response").path("requests");
		if (peticiones != null && peticiones.has("limit_day") && peticiones.has("current")) {
			restantesDia = peticiones.path("limit_day").asInt() - peticiones.path("current").asInt();
		}
	}

	/**
	 * La cabecera x-ratelimit-requests-remaining llega con retraso respecto a las peticiones recientes,
	 * así que se descuenta cada petición localmente y se toma el valor más bajo de los dos.
	 */
	private void descontarPeticion(ResponseEntity<JsonNode> respuesta) {
		Integer porCabecera = null;
		String cabecera = respuesta.getHeaders().getFirst("x-ratelimit-requests-remaining");
		if (cabecera != null) {
			try {
				porCabecera = Integer.parseInt(cabecera.trim());
			} catch (NumberFormatException ignorado) {
				// Cabecera inesperada: se usa solo la cuenta local.
			}
		}
		if (restantesDia != null) {
			restantesDia = porCabecera != null ? Math.min(restantesDia - 1, porCabecera) : restantesDia - 1;
		} else {
			restantesDia = porCabecera;
		}
	}

	private ResponseEntity<JsonNode> llamar(String ruta) {
		esperarIntervalo();
		try {
			return client.get().uri(ruta).header("x-apisports-key", clave).retrieve().toEntity(JsonNode.class);
		} catch (RestClientException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al llamar a API-Football: " + e.getMessage());
		} finally {
			ultimaPeticionMs = System.currentTimeMillis();
		}
	}

	private void esperarIntervalo() {
		long espera = intervaloMs - (System.currentTimeMillis() - ultimaPeticionMs);
		if (espera > 0) {
			try {
				Thread.sleep(espera);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Importación interrumpida");
			}
		}
	}
}
