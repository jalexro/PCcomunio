package com.ligacomunio.valores;

import com.ligacomunio.player.PlayerStatus;
import com.ligacomunio.player.Position;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Fila guardada de la última importación de valores (ver {@code V8}). */
@Entity
@Getter
@Setter
public class ExternalValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String slug;

	@Column(nullable = false)
	private String teamName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Position position;

	@Column(nullable = false)
	private long value;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlayerStatus status = PlayerStatus.DISPONIBLE;

	static ExternalValue de(ValorExterno v) {
		ExternalValue e = new ExternalValue();
		e.name = v.nombre();
		e.slug = v.slug();
		e.teamName = v.equipo();
		e.position = v.posicion();
		e.value = v.valor();
		e.status = v.estado();
		return e;
	}

	ValorExterno aValor() {
		return new ValorExterno(name, slug, teamName, position, value, status);
	}
}
