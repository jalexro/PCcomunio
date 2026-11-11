package com.ligacomunio.config;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuerpo de error uniforme {"message": ...}. Solo se expone el texto de las excepciones que lanzamos
 * nosotros a propósito (ResponseStatusException) y los fallos de validación; los errores inesperados
 * siguen el camino por defecto de Spring, que no revela detalles internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<Map<String, Object>> estado(ResponseStatusException e) {
		HttpStatusCode codigo = e.getStatusCode();
		String mensaje = e.getReason() != null ? e.getReason() : "Error";
		return ResponseEntity.status(codigo).body(Map.of("message", mensaje));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
		Map<String, String> errores = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> errores.putIfAbsent(f.getField(), f.getDefaultMessage()));
		return ResponseEntity.badRequest().body(Map.of("message", "Datos no válidos", "errors", errores));
	}
}
