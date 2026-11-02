package com.rentequip.backend.services;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Renders a Thymeleaf template and sends it as an HTML email.
 *
 * <p>Sending never propagates a failure. These messages are notifications about something that has
 * already been committed, so an unreachable SMTP server must not undo a reservation nor surface as a
 * 500 to a client whose request succeeded minutes ago.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String LOGO_CONTENT_ID = "rentequip-logo";
    private static final ClassPathResource LOGO = new ClassPathResource("static-mail/rentequip-logo.png");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${rentequip.mail.enabled}")
    private boolean enabled;

    @Value("${rentequip.mail.from}")
    private String from;

    public void send(String to, String subject, String template, Map<String, Object> variables) {
        String body = render(template, variables);
        if (!enabled) {
            log.info("Mail disabled, skipping '{}' to {} (template {})", subject, to, template);
            return;
        }
        try {
            mailSender.send(buildMessage(to, subject, body));
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception failure) {
            log.warn("Could not send '{}' to {}: {}", subject, to, failure.getMessage());
        }
    }

    /**
     * For messages the user asked for (e.g. "send me the receipt"): a failure is reported instead of
     * swallowed, so the screen can tell the user that nothing arrived.
     */
    public void sendOrFail(String to, String subject, String template, Map<String, Object> variables) {
        if (!enabled) {
            throw NotificationDeliveryException.notConfigured("correo");
        }
        try {
            mailSender.send(buildMessage(to, subject, render(template, variables)));
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception failure) {
            log.warn("Could not send '{}' to {}: {}", subject, to, failure.getMessage());
            throw NotificationDeliveryException.failed("No pudimos enviar el correo. Inténtalo en unos minutos.");
        }
    }

    private MimeMessage buildMessage(String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        // Inline attachment referenced as cid:rentequip-logo: shows in Gmail and Outlook, unlike SVG or data URIs.
        helper.addInline(LOGO_CONTENT_ID, LOGO, MediaType.IMAGE_PNG_VALUE);
        return message;
    }

    private String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process("mail/" + template, context);
    }
}
