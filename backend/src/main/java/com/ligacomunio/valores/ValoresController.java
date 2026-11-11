package com.ligacomunio.valores;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/** Solo ADMIN (lo exige {@code SecurityConfig} para {@code /api/admin/**}). */
@RestController
@RequestMapping("/api/admin/import/values")
public class ValoresController {

	private final BiwengerParser parser;
	private final ValueImportService service;

	public ValoresController(BiwengerParser parser, ValueImportService service) {
		this.parser = parser;
		this.service = service;
	}

	/** Recibe el JSON de Biwenger guardado desde el navegador (campo multipart {@code file}). */
	@PostMapping("/biwenger")
	public ValoresResultado biwenger(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío.");
		}
		try (InputStream contenido = file.getInputStream()) {
			return service.importar(parser.leer(contenido));
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo.");
		}
	}
}
