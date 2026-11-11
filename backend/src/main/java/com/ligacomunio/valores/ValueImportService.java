package com.ligacomunio.valores;

import com.ligacomunio.player.Player;
import com.ligacomunio.player.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Pone el valor de mercado y el estado (lesionado, sancionado, duda) a los jugadores de la base de datos
 * emparejándolos con los de una fuente externa. Las estadísticas son de una temporada y los valores de otra, así
 * que no hay identificador común: se empareja por equipo y nombre, y solo se acepta un emparejamiento si es único
 * (ante la duda el jugador queda sin valor y se lista en el informe). Los jugadores sin emparejar conservan su
 * valor anterior. La última fuente importada se guarda para reaplicarla cuando aparecen jugadores nuevos.
 */
@Service
public class ValueImportService {

	private final PlayerRepository playerRepository;
	private final ExternalValueRepository valoresGuardados;

	public ValueImportService(PlayerRepository playerRepository, ExternalValueRepository valoresGuardados) {
		this.playerRepository = playerRepository;
		this.valoresGuardados = valoresGuardados;
	}

	/** Sustituye la fuente guardada por la nueva y la aplica. */
	@Transactional
	public ValoresResultado importar(List<ValorExterno> valores) {
		valoresGuardados.deleteAllInBatch();
		valoresGuardados.saveAll(valores.stream().map(ExternalValue::de).toList());
		return aplicar(valores);
	}

	/** Vuelve a aplicar la última fuente guardada (p. ej. tras importar jugadores nuevos). No hace nada si no hay. */
	@Transactional
	public ValoresResultado reaplicar() {
		List<ValorExterno> valores = valoresGuardados.findAll().stream().map(ExternalValue::aValor).toList();
		return valores.isEmpty() ? null : aplicar(valores);
	}

	ValoresResultado aplicar(List<ValorExterno> valores) {
		List<Player> jugadores = playerRepository.buscar(null, null);
		Set<Long> asignados = new HashSet<>();
		Set<String> equiposSinCorrespondencia = new TreeSet<>();
		Player[] encontrados = new Player[valores.size()];
		boolean[] otroEquipo = new boolean[valores.size()];

		// Primera pasada, la fiable: dentro del equipo del jugador.
		for (int i = 0; i < valores.size(); i++) {
			ValorExterno v = valores.get(i);
			if (jugadores.stream().noneMatch(p -> Normalizacion.equipoCompatible(p.getTeam().getName(), v.equipo()))) {
				equiposSinCorrespondencia.add(v.equipo());
			}
			List<Player> delEquipo = jugadores.stream()
					.filter(p -> Normalizacion.equipoCompatible(p.getTeam().getName(), v.equipo()))
					.filter(p -> !asignados.contains(p.getId()))
					.toList();
			Player p = buscarEn(delEquipo, v);
			if (p != null) {
				encontrados[i] = p;
				asignados.add(p.getId());
			}
		}

		// Segunda pasada, solo con lo que queda: fichajes que cambiaron de equipo desde la temporada de las estadísticas.
		for (int i = 0; i < valores.size(); i++) {
			if (encontrados[i] != null) {
				continue;
			}
			ValorExterno v = valores.get(i);
			List<Player> libres = jugadores.stream().filter(p -> !asignados.contains(p.getId())).toList();
			Player p = buscarPorNombreCompleto(libres, v);
			if (p == null) {
				p = buscarPorNombreAbreviado(libres, v);
			}
			if (p != null) {
				encontrados[i] = p;
				otroEquipo[i] = true;
				asignados.add(p.getId());
			}
		}

		int emparejados = 0;
		int enOtroEquipo = 0;
		List<String> sinEmparejar = new ArrayList<>();
		for (int i = 0; i < valores.size(); i++) {
			ValorExterno v = valores.get(i);
			Player p = encontrados[i];
			if (p == null) {
				sinEmparejar.add(v.nombre() + " (" + v.equipo() + ")");
				continue;
			}
			p.setMarketValue(BigDecimal.valueOf(v.valor()));
			p.setStatus(v.estado());
			emparejados++;
			if (otroEquipo[i]) {
				enOtroEquipo++;
			}
		}

		List<String> sinValor = jugadores.stream()
				.filter(p -> p.getMarketValue().signum() <= 0)
				.map(p -> p.getName() + " (" + p.getTeam().getName() + ")")
				.toList();
		return new ValoresResultado(valores.size(), emparejados, enOtroEquipo, sinEmparejar,
				new ArrayList<>(equiposSinCorrespondencia), sinValor);
	}

	/** Busca dentro de un equipo, de lo más estricto a lo más laxo; devuelve algo solo si el resultado es único. */
	private static Player buscarEn(List<Player> candidatos, ValorExterno v) {
		List<String> nombre = Normalizacion.palabras(v.nombre());
		List<String> slug = Normalizacion.palabras(v.slug().replace('-', ' '));
		List<Predicate<List<String>>> niveles = List.of(
				// Nombre completo idéntico al nombre o al slug.
				p -> p.equals(nombre) || p.equals(slug),
				// Todas las palabras del nombre (o del slug) están en el nombre de la base de datos ("Balde" en "Alejandro Balde").
				p -> (!nombre.isEmpty() && p.containsAll(nombre)) || (!slug.isEmpty() && p.containsAll(slug)),
				// Todas las palabras del nombre de la base de datos (al menos dos) están en el slug.
				p -> p.size() >= 2 && slug.containsAll(p),
				// Nombre abreviado: "a-gorosabel" ~ "Andoni Gorosabel", "abdel-abqar" ~ "Abdelkabir Abqar".
				p -> abreviaturaDe(slug, p));
		for (Predicate<List<String>> nivel : niveles) {
			List<Player> coincidencias = candidatos.stream()
					.filter(p -> nivel.test(Normalizacion.palabras(p.getName())))
					.toList();
			if (coincidencias.size() == 1) {
				return coincidencias.get(0);
			}
			if (coincidencias.size() > 1) {
				List<Player> mismaPosicion = coincidencias.stream().filter(p -> p.getPosition() == v.posicion()).toList();
				return mismaPosicion.size() == 1 ? mismaPosicion.get(0) : null;
			}
		}
		return null;
	}

	/** Respaldo para fichajes: solo nombre completo (al menos dos palabras) idéntico y único en toda la liga. */
	private static Player buscarPorNombreCompleto(List<Player> candidatos, ValorExterno v) {
		List<String> nombre = Normalizacion.palabras(v.nombre());
		List<String> slug = Normalizacion.palabras(v.slug().replace('-', ' '));
		List<Player> coincidencias = candidatos.stream()
				.filter(p -> {
					List<String> palabras = Normalizacion.palabras(p.getName());
					return palabras.size() >= 2 && (palabras.equals(nombre) || palabras.equals(slug));
				})
				.toList();
		return coincidencias.size() == 1 ? coincidencias.get(0) : null;
	}

	/** Respaldo para fichajes con nombre abreviado: misma posición y único en toda la liga. */
	private static Player buscarPorNombreAbreviado(List<Player> candidatos, ValorExterno v) {
		List<String> slug = Normalizacion.palabras(v.slug().replace('-', ' '));
		List<Player> coincidencias = candidatos.stream()
				.filter(p -> p.getPosition() == v.posicion() && abreviaturaDe(slug, Normalizacion.palabras(p.getName())))
				.toList();
		return coincidencias.size() == 1 ? coincidencias.get(0) : null;
	}

	/**
	 * El slug es una abreviatura del nombre completo: tiene al menos dos palabras, el apellido (última palabra) es
	 * el mismo y la primera palabra del slug es el principio de la primera del nombre ("a" de "andoni").
	 */
	private static boolean abreviaturaDe(List<String> slug, List<String> completo) {
		return slug.size() >= 2 && completo.size() >= 2
				&& slug.get(slug.size() - 1).equals(completo.get(completo.size() - 1))
				&& completo.get(0).startsWith(slug.get(0));
	}
}
