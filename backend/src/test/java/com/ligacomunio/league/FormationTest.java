package com.ligacomunio.league;

import com.ligacomunio.player.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormationTest {

	private List<Position> once(int defensas, int medios, int delanteros) {
		List<Position> posiciones = new ArrayList<>();
		posiciones.add(Position.PORTERO);
		posiciones.addAll(Collections.nCopies(defensas, Position.DEFENSA));
		posiciones.addAll(Collections.nCopies(medios, Position.CENTROCAMPISTA));
		posiciones.addAll(Collections.nCopies(delanteros, Position.DELANTERO));
		return posiciones;
	}

	@Test
	void todasLasTacticasSumanOnceConElPortero() {
		for (Formation f : Formation.values()) {
			assertThat(1 + f.defensas() + f.medios() + f.delanteros()).as(f.codigo()).isEqualTo(Formation.TITULARES);
		}
	}

	@Test
	void cadaTacticaAceptaSuPropiaAlineacion() {
		for (Formation f : Formation.values()) {
			assertThat(f.motivoDeRechazo(once(f.defensas(), f.medios(), f.delanteros()))).as(f.codigo()).isNull();
		}
	}

	@Test
	void unaAlineacionDeOtraTacticaSeRechazaConUnMensajeClaro() {
		String motivo = Formation.F433.motivoDeRechazo(once(4, 4, 2));

		assertThat(motivo).contains("4-3-3").contains("3 delanteros").contains("has elegido");
	}

	@Test
	void sinPorteroSeRechaza() {
		List<Position> sinPortero = new ArrayList<>(once(4, 4, 2));
		sinPortero.set(0, Position.DEFENSA);

		assertThat(Formation.F442.motivoDeRechazo(sinPortero)).isNotNull();
	}

	@Test
	void conMenosOMasDeOnceSeRechaza() {
		assertThat(Formation.F442.motivoDeRechazo(once(4, 4, 1))).contains("exactamente 11");
		assertThat(Formation.F442.motivoDeRechazo(once(5, 4, 2))).contains("exactamente 11");
	}

	@Test
	void elCodigoDeLaApiSeConvierteEnLaTactica() {
		assertThat(Formation.deCodigo("3-5-2")).isEqualTo(Formation.F352);
		assertThat(Formation.F532.codigo()).isEqualTo("5-3-2");
		assertThatThrownBy(() -> Formation.deCodigo("1-1-8")).isInstanceOf(IllegalArgumentException.class);
	}
}
