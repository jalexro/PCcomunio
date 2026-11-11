package com.ligacomunio.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío de correo con modo de desarrollo: si no hay {@code MAIL_HOST} configurado, en lugar de enviar
 * escribe el mensaje en el log. Así la verificación de email y el reset de contraseña se pueden probar
 * de principio a fin sin dar de alta un proveedor SMTP: el enlace sale en la consola del backend.
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);

	private final ObjectProvider<JavaMailSender> sender;
	private final String remitente;
	private final boolean activo;

	public Mailer(ObjectProvider<JavaMailSender> sender, AuthProperties propiedades,
	              @Value("${spring.mail.host:}") String host) {
		this.sender = sender;
		this.remitente = propiedades.remitente();
		// La autoconfiguración de Spring puede crear el bean aunque el host venga vacío, así que
		// se comprueba la propiedad y no la presencia del bean.
		this.activo = !host.isBlank();
	}

	/** Nunca propaga un fallo de envío: quien llama no debe poder distinguir si el email existe. */
	public void enviar(String destinatario, String asunto, String cuerpo) {
		if (!activo) {
			log.info("""
					[correo no enviado: MAIL_HOST sin configurar]
					Para: {}
					Asunto: {}
					{}""", destinatario, asunto, cuerpo);
			return;
		}
		try {
			SimpleMailMessage mensaje = new SimpleMailMessage();
			mensaje.setFrom(remitente);
			mensaje.setTo(destinatario);
			mensaje.setSubject(asunto);
			mensaje.setText(cuerpo);
			sender.getObject().send(mensaje);
		} catch (RuntimeException e) {
			log.error("No se pudo enviar el correo «{}»", asunto, e);
		}
	}
}
