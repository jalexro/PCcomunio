package com.ligacomunio.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Solo memoria: ni base de datos ni contexto de Spring. */
class ControlDeIntentosTest {

	private static final String EMAIL = "login:email:javi@correo.com";
	private static final String IP = "login:ip:10.0.0.1";

	/** Las propiedades solo importan en los tres primeros campos; el resto va con valores cualesquiera. */
	private ControlDeIntentos control(int maxIntentos, long bloqueoSegundos, long ventanaSegundos) {
		return new ControlDeIntentos(new AuthProperties(maxIntentos, bloqueoSegundos, ventanaSegundos,
				false, 48, 30, "http://localhost:5173", "no-reply@test", 3600000));
	}

	@Test
	void sinFallosNoBloqueaNada() {
		assertThatCode(() -> control(3, 900, 900).comprobar(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void porDebajoDelMaximoTodaviaDejaIntentarlo() {
		ControlDeIntentos control = control(3, 900, 900);

		control.fallo(EMAIL, IP);
		control.fallo(EMAIL, IP);

		assertThatCode(() -> control.comprobar(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void alLlegarAlMaximoDevuelve429ConElTiempoQueFalta() {
		ControlDeIntentos control = control(3, 900, 900);

		for (int i = 0; i < 3; i++) {
			control.fallo(EMAIL, IP);
		}

		assertThatThrownBy(() -> control.comprobar(EMAIL, IP))
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
					assertThat(e.getReason()).isEqualTo("Demasiados intentos fallidos. Vuelve a probar en 15 minutos.");
				});
	}

	@Test
	void bastaConQueUnaDeLasClavesEsteBloqueada() {
		ControlDeIntentos control = control(2, 900, 900);

		// Dos fallos desde la misma IP pero con emails distintos: la clave de la IP se bloquea.
		control.fallo("login:email:uno@correo.com", IP);
		control.fallo("login:email:dos@correo.com", IP);

		assertThatThrownBy(() -> control.comprobar("login:email:tres@correo.com", IP))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void unBloqueoNoAlcanzaAOtrasClaves() {
		ControlDeIntentos control = control(2, 900, 900);

		control.fallo(EMAIL, IP);
		control.fallo(EMAIL, IP);

		assertThatCode(() -> control.comprobar("login:email:otra@correo.com", "login:ip:10.0.0.2"))
				.doesNotThrowAnyException();
	}

	@Test
	void elExitoBorraElHistorialDeFallos() {
		ControlDeIntentos control = control(3, 900, 900);

		control.fallo(EMAIL, IP);
		control.fallo(EMAIL, IP);
		control.exito(EMAIL, IP);
		// Si el contador no se hubiera reiniciado, este tercer fallo bloquearía.
		control.fallo(EMAIL, IP);

		assertThatCode(() -> control.comprobar(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void elBloqueoCaducaSolo() throws InterruptedException {
		ControlDeIntentos control = control(2, 1, 900);

		control.fallo(EMAIL);
		control.fallo(EMAIL);
		assertThatThrownBy(() -> control.comprobar(EMAIL)).isInstanceOf(ResponseStatusException.class);

		Thread.sleep(1100);

		assertThatCode(() -> control.comprobar(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void laLimpiezaNoLevantaUnBloqueoVigente() {
		ControlDeIntentos control = control(2, 900, 900);

		control.fallo(EMAIL);
		control.fallo(EMAIL);
		control.limpiar();

		assertThatThrownBy(() -> control.comprobar(EMAIL)).isInstanceOf(ResponseStatusException.class);
		assertThat(contadores(control)).containsKey(EMAIL);
	}

	@Test
	void laLimpiezaVaciaLasClavesQueYaNoBloquean() throws InterruptedException {
		ControlDeIntentos control = control(2, 1, 1);

		control.fallo(EMAIL);
		control.fallo(EMAIL);
		Thread.sleep(1100);
		control.limpiar();

		// El mapa vive en memoria para siempre: sin esta limpieza crecería con cada email probado.
		assertThat(contadores(control)).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, ?> contadores(ControlDeIntentos control) {
		return (Map<String, ?>) ReflectionTestUtils.getField(control, "contadores");
	}

	@Test
	void unBloqueoDeMenosDeUnMinutoSeCuentaEnSegundos() {
		ControlDeIntentos control = control(1, 30, 900);

		control.fallo(EMAIL);

		assertThatThrownBy(() -> control.comprobar(EMAIL))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getReason()).contains("segundos"));
	}
}
