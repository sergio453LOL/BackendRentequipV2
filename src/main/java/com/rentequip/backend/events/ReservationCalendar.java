package com.rentequip.backend.events;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A confirmed reservation as a calendar event, in the two forms the renter can use: an iCalendar invitation
 * attached to the confirmation email (Gmail and Outlook add it to the calendar on their own), and a Google
 * Calendar link for the button in the email and on the web. A cancellation sends the same event with
 * METHOD:CANCEL, which takes it off the calendar again.
 *
 * <p>The event is all-day and covers every rented day: iCalendar and Google take the end date as exclusive,
 * so it is the day after the last one.
 */
public final class ReservationCalendar {

    /** The text of a .ics file and its iTIP method, which also goes in the attachment's content type. */
    public record CalendarInvite(String method, String content) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String GOOGLE_TEMPLATE = "https://calendar.google.com/calendar/render?action=TEMPLATE";

    private ReservationCalendar() {
    }

    public static CalendarInvite invite(ReservationSnapshot reservation, String organizerEmail) {
        return build(reservation, organizerEmail, "REQUEST", "CONFIRMED", 0);
    }

    public static CalendarInvite cancellation(ReservationSnapshot reservation, String organizerEmail) {
        return build(reservation, organizerEmail, "CANCEL", "CANCELLED", 1);
    }

    /** Opens Google Calendar with the event filled in; the renter only has to press Save. */
    public static String googleCalendarUrl(ReservationSnapshot reservation) {
        return GOOGLE_TEMPLATE
                + "&text=" + encode(title(reservation))
                + "&dates=" + DAY.format(reservation.startDate()) + "/" + DAY.format(exclusiveEnd(reservation))
                + "&details=" + encode(description(reservation))
                + (hasText(reservation.equipmentAddress()) ? "&location=" + encode(reservation.equipmentAddress()) : "")
                + "&ctz=America/Lima";
    }

    private static CalendarInvite build(ReservationSnapshot reservation, String organizerEmail, String method,
                                        String status, int sequence) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("PRODID:-//RentEquip//Reservas//ES");
        lines.add("VERSION:2.0");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:" + method);
        lines.add("BEGIN:VEVENT");
        // The same UID on the confirmation and the cancellation is what lets the calendar match them.
        lines.add("UID:reserva-" + reservation.reservationId() + "@rentequip.pe");
        lines.add("SEQUENCE:" + sequence);
        lines.add("DTSTAMP:" + STAMP.format(Instant.now()));
        lines.add("DTSTART;VALUE=DATE:" + DAY.format(reservation.startDate()));
        lines.add("DTEND;VALUE=DATE:" + DAY.format(exclusiveEnd(reservation)));
        lines.add("SUMMARY:" + escape(title(reservation)));
        lines.add("DESCRIPTION:" + escape(description(reservation)));
        if (hasText(reservation.equipmentAddress())) {
            lines.add("LOCATION:" + escape(reservation.equipmentAddress()));
        }
        lines.add("ORGANIZER;CN=" + quoted(reservation.ownerCompanyName()) + ":mailto:" + organizerEmail);
        lines.add("ATTENDEE;CN=" + quoted(reservation.renterCompanyName())
                + ";ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=FALSE:mailto:" + reservation.renterEmail());
        lines.add("STATUS:" + status);
        lines.add("TRANSP:OPAQUE");
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        StringBuilder content = new StringBuilder();
        lines.forEach(line -> content.append(fold(line)).append("\r\n"));
        return new CalendarInvite(method, content.toString());
    }

    private static String title(ReservationSnapshot reservation) {
        return "Alquiler: " + reservation.equipmentName() + " (RentEquip)";
    }

    private static String description(ReservationSnapshot reservation) {
        return "Reserva #" + reservation.reservationId() + " confirmada por " + reservation.ownerCompanyName() + ".\n"
                + "Maquinaria: " + reservation.equipmentName() + "\n"
                + "Días: " + reservation.totalDays() + "\n"
                + "Total: " + reservation.currency() + " " + reservation.totalAmount().setScale(2) + "\n"
                + "Contacto del propietario: " + reservation.ownerEmail();
    }

    private static LocalDate exclusiveEnd(ReservationSnapshot reservation) {
        return reservation.endDate().plusDays(1);
    }

    /** RFC 5545 text: backslash, semicolon, comma and line breaks are escaped. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /** Parameter values with separators must be quoted, and may not contain quotes themselves. */
    private static String quoted(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }

    /** Lines longer than 75 octets continue on the next line after a space, without splitting a character. */
    private static String fold(String line) {
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        for (int offset = 0; offset < line.length(); ) {
            int codePoint = line.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > 75) {
                folded.append("\r\n ");
                octets = 1;
            }
            folded.append(character);
            octets += size;
            offset += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
