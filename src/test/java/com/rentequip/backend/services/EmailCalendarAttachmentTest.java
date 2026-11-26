package com.rentequip.backend.services;

import com.rentequip.backend.events.ReservationCalendar.CalendarInvite;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The invitation travels as a text/calendar attachment whose content type names the iTIP method: that is
 * what Gmail and Outlook look for to add the event to the calendar.
 */
class EmailCalendarAttachmentTest {

    @Test
    void theInvitationIsAttachedAsACalendarFile() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        TemplateEngine templates = mock(TemplateEngine.class);
        when(templates.process(anyString(), any(org.thymeleaf.context.IContext.class))).thenReturn("<p>Hola</p>");
        EmailService emailService = new EmailService(sender, templates);
        ReflectionTestUtils.setField(emailService, "enabled", true);
        ReflectionTestUtils.setField(emailService, "from", "no-reply@rentequip.pe");

        String ics = "BEGIN:VCALENDAR\r\nMETHOD:REQUEST\r\nEND:VCALENDAR\r\n";
        emailService.send("renter@rentequip.pe", "Reserva confirmada", "reservation-confirmed", Map.of(),
                new CalendarInvite("REQUEST", ics));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        Part calendar = parts(message).stream()
                .filter(part -> "reserva.ics".equals(fileName(part)))
                .findFirst().orElseThrow();
        assertThat(calendar.getContentType()).startsWith("text/calendar").contains("method=REQUEST");
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        calendar.getDataHandler().writeTo(content);
        assertThat(content.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(ics);
    }

    private static List<Part> parts(Part part) throws Exception {
        List<Part> all = new ArrayList<>();
        all.add(part);
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                all.addAll(parts(child));
            }
        }
        return all;
    }

    private static String fileName(Part part) {
        try {
            return part.getFileName();
        } catch (Exception unreadable) {
            return null;
        }
    }
}
