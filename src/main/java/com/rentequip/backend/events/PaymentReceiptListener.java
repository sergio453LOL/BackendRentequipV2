package com.rentequip.backend.events;

import com.rentequip.backend.config.AsyncConfig;
import com.rentequip.backend.services.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the payment receipt as soon as a reservation is paid, for every client (web and mobile). The web's
 * payment screen then sends the WhatsApp and SMS copies itself, so it can show how each channel went and
 * offer to resend the one that failed.
 *
 * <p>AFTER_COMMIT so a checkout that rolls back never sends a receipt, and async so the mail server's
 * latency is not added to the payment response.
 */
@Component
@RequiredArgsConstructor
public class PaymentReceiptListener {

    private final ReceiptService receiptService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationPaid(ReservationPaidEvent event) {
        receiptService.emailAfterPayment(event.reservationId(), event.payerUserId());
    }
}
