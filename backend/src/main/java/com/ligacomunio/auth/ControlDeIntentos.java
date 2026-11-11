package com.ligacomunio.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a la fuerza bruta en el login: tras {@code app.auth.max-intentos} fallos seguidos se
 * responde 429 durante {@code app.auth.bloqueo-segundos}. Se cuenta por email <em>y</em> por IP,
 * así que ni se puede machacar una cuenta concreta ni probar muchas cuentas desde una misma IP.
 *
 * <p>El estado vive en memoria. Con una sola instancia del backend basta; si algún día se levantan
 * varias habrá que llevarlo a Redis o a la base de datos, porque cada una contaría por su cuenta.
 */
@Component
public class ControlDeIntentos {

	private static final Logger log = LoggerFactory.getLogger(ControlDeIntentos.class);

	private final int maxIntentos;
	private final Duration bloqueo;
	private final Duration ventana;
	private final Map<String, Contador> contadores = new ConcurrentHashMap<>();

	public ControlDeIntentos(AuthProperties propiedades) {
		this.maxIntentos = propiedades.maxIntentos();
		this.bloqueo = Duration.ofSeconds(propiedades.bloqueoSegundos());
		this.ventana = Duration.ofSeconds(propiedades.ventanaSegundos());
	}

	/** Lanza 429 si alguna de las claves está bloqueada ahora mismo. */
	public void comprobar(String... claves) {
		Instant ahora = Instant.now();
		for (String clave : claves) {
			Contador contador = contadores.get(clave);
			if (contador == null) {
				continue;
			}
			// Copia local: otro hilo puede estar escribiendo el campo a la vez.
			Instant hasta = contador.bloqueadoHasta;
			if (hasta != null && hasta.isAfter(ahora)) {
				long segundos = Math.max(1, Duration.between(ahora, hasta).toSeconds());
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
						"Demasiados intentos fallidos. Vuelve a probar en " + minutosLegibles(segundos) + ".");
			}
		}
	}

	/** Un intento fallido: suma a cada clave y bloquea la que llegue al máximo. */
	public void fallo(String... claves) {
		Instant ahora = Instant.now();
		for (String clave : claves) {
			contadores.compute(clave, (k, contador) -> {
				Contador actual = contador != null ? contador : new Contador();
				// Si lleva mucho sin fallar, se empieza a contar de nuevo.
				boolean caducado = actual.ultimoFallo == null || actual.ultimoFallo.plus(ventana).isBefore(ahora);
				actual.fallos = caducado ? 1 : actual.fallos + 1;
				actual.ultimoFallo = ahora;
				if (actual.fallos >= maxIntentos) {
					actual.bloqueadoHasta = ahora.plus(bloqueo);
					actual.fallos = 0;
					log.warn("Bloqueados los intentos de {} durante {} s", k, bloqueo.toSeconds());
				}
				return actual;
			});
		}
	}

	/** Un intento correcto: borra el historial de esas claves. */
	public void exito(String... claves) {
		for (String clave : claves) {
			contadores.remove(clave);
		}
	}

	/** Evita que el mapa crezca sin fin con claves que ya no interesan. */
	public void limpiar() {
		Instant ahora = Instant.now();
		contadores.values().removeIf(c -> {
			boolean bloqueoVencido = c.bloqueadoHasta == null || c.bloqueadoHasta.isBefore(ahora);
			boolean sinFallosRecientes = c.ultimoFallo == null || c.ultimoFallo.plus(ventana).isBefore(ahora);
			return bloqueoVencido && sinFallosRecientes;
		});
	}

	private String minutosLegibles(long segundos) {
		if (segundos < 60) {
			return segundos + " segundos";
		}
		long minutos = (segundos + 59) / 60;
		return minutos == 1 ? "un minuto" : minutos + " minutos";
	}

	private static final class Contador {
		private volatile int fallos;
		private volatile Instant ultimoFallo;
		private volatile Instant bloqueadoHasta;
	}
}
