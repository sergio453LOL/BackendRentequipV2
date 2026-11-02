package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.ReceiptDeliveryResponse;
import com.rentequip.backend.entities.Payment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.PaymentType;
import com.rentequip.backend.enums.ReceiptChannel;
import com.rentequip.backend.enums.ReservationPaymentStatus;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.notifications.WhatsAppClient;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the payment receipt of a paid reservation to the renter, on demand: by email to the user's
 * login address, or by WhatsApp to the phone registered for the user (or, if missing, for the company).
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** What the email template and the WhatsApp text show, already formatted. */
    public record ReceiptSummary(String code, String renterName, String equipmentName, String dates, String rental,
                                 String deposit, String total, String paymentReference) {
    }

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final EmailService emailService;
    private final WhatsAppClient whatsAppClient;

    @Transactional(readOnly = true)
    public ReceiptDeliveryResponse send(Long reservationId, ReceiptChannel channel) {
        Long companyId = currentUser.requireCompanyId();
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
        if (!reservation.getRenter().getId().equals(companyId)) {
            throw new ForbiddenOperationException("Only the renting company can receive this receipt");
        }
        if (PaymentService.paymentStatusOf(reservation) != ReservationPaymentStatus.PAID) {
            throw new InvalidOperationException("Esta reserva todavía no tiene un pago completado");
        }
        User user = userRepository.findById(currentUser.requireUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", currentUser.requireUserId()));
        ReceiptSummary receipt = summarize(reservation);

        return switch (channel) {
            case EMAIL -> {
                emailService.sendOrFail(user.getEmail(), "Comprobante " + receipt.code() + " · RentEquip",
                        "payment-receipt", Map.of("receipt", receipt));
                yield new ReceiptDeliveryResponse(channel, maskEmail(user.getEmail()));
            }
            case WHATSAPP -> {
                String phone = toE164(user.getPhone() != null && !user.getPhone().isBlank()
                        ? user.getPhone() : reservation.getRenter().getPhone());
                whatsAppClient.send(phone, whatsAppText(receipt));
                yield new ReceiptDeliveryResponse(channel, maskPhone(phone));
            }
        };
    }

    private ReceiptSummary summarize(Reservation reservation) {
        String currency = reservation.getCurrency();
        String reference = reservation.getPayments().stream()
                .filter(payment -> payment.getType() == PaymentType.RENTAL)
                .map(Payment::getProviderReference)
                .findFirst()
                .orElse(null);
        return new ReceiptSummary(
                "RE-%06d".formatted(reservation.getId()),
                reservation.getRenter().getName(),
                reservation.getEquipment().getName(),
                "Del %s al %s (%d %s)".formatted(reservation.getStartDate().format(DAY), reservation.getEndDate().format(DAY),
                        reservation.getTotalDays(), reservation.getTotalDays() == 1 ? "día" : "días"),
                money(reservation.getRentalAmount(), currency),
                money(reservation.getSecurityDeposit(), currency),
                money(reservation.getTotalAmount(), currency),
                reference);
    }

    /** WhatsApp formatting: *bold*, one fact per line so it reads well on a phone. */
    private static String whatsAppText(ReceiptSummary receipt) {
        StringBuilder text = new StringBuilder()
                .append("*RentEquip* · Comprobante ").append(receipt.code()).append("\n\n")
                .append(receipt.equipmentName()).append('\n')
                .append(receipt.dates()).append("\n\n")
                .append("Alquiler (cobrado): ").append(receipt.rental()).append('\n')
                .append("Garantía (retenida): ").append(receipt.deposit()).append('\n')
                .append("*Total: ").append(receipt.total()).append("*\n");
        if (receipt.paymentReference() != null) {
            text.append("\nRef. de pago: ").append(receipt.paymentReference()).append('\n');
        }
        return text.append("\nGracias por alquilar con RentEquip.").toString();
    }

    private static String money(BigDecimal amount, String currency) {
        String symbol = switch (currency) {
            case "PEN" -> "S/";
            case "USD" -> "US$";
            default -> currency;
        };
        return "%s %,.2f".formatted(symbol, amount).replace(' ', ' ');
    }

    /** Peruvian mobiles are stored as typed (987654321, +51 987 654 321...); Twilio needs +51987654321. */
    static String toE164(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new InvalidOperationException("No tienes un celular registrado para recibir el comprobante");
        }
        String digits = phone.replaceAll("\\D", "");
        if (phone.trim().startsWith("+") && digits.length() >= 8) return "+" + digits;
        if (digits.length() == 9 && digits.startsWith("9")) return "+51" + digits;
        if (digits.length() == 11 && digits.startsWith("519")) return "+" + digits;
        throw new InvalidOperationException("El celular registrado no es un número válido: actualízalo en tu perfil");
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at).toLowerCase(Locale.ROOT);
    }

    private static String maskPhone(String e164) {
        String lastThree = e164.substring(e164.length() - 3);
        return e164.startsWith("+51") ? "+51 9•• ••• " + lastThree : "•••" + lastThree;
    }
}
