package com.rentequip.backend.services;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private MimeMessage buildMessage(String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        return message;
    }

    private String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process("mail/" + template, context);
    }
}
