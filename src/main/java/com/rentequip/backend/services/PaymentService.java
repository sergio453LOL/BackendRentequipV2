package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.PaymentRequest;
import com.rentequip.backend.dtos.response.PaymentResponse;
import com.rentequip.backend.entities.Payment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.PaymentStatus;
import com.rentequip.backend.enums.PaymentType;
import com.rentequip.backend.enums.ReservationPaymentStatus;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.PaymentDeclinedException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.PaymentMapper;
import com.rentequip.backend.payments.PaymentGateway;
import com.rentequip.backend.payments.PaymentGateway.GatewayResult;
import com.rentequip.backend.repositories.PaymentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Checkout of a reservation: the rental is captured and the security deposit is held on the card. When the
 * reservation is cancelled or rejected the money goes back, and when it completes the hold is released.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Set<ReservationStatus> PAYABLE_STATUSES =
            Set.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentMapper paymentMapper;
    private final CurrentUser currentUser;

    @Transactional
    public List<PaymentResponse> pay(Long reservationId, PaymentRequest request) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));

        if (!reservation.getRenter().getId().equals(actingCompanyId)) {
            throw new ForbiddenOperationException("Only the renting company can pay this reservation");
        }
        if (!PAYABLE_STATUSES.contains(reservation.getStatus())) {
            throw new InvalidOperationException("A " + reservation.getStatus() + " reservation cannot be paid");
        }
        if (paymentStatusOf(reservation) != ReservationPaymentStatus.UNPAID) {
            throw new InvalidOperationException("This reservation is already paid");
        }

        String description = "Reserva #" + reservation.getId() + " - " + reservation.getEquipment().getName();
        GatewayResult rental = paymentGateway.charge(request.paymentToken(), reservation.getRentalAmount(),
                reservation.getCurrency(), description);
        if (!rental.approved()) {
            throw new PaymentDeclinedException(rental.declineReason());
        }

        GatewayResult deposit = null;
        if (reservation.getSecurityDeposit().signum() > 0) {
            deposit = paymentGateway.authorize(request.paymentToken(), reservation.getSecurityDeposit(),
                    reservation.getCurrency(), description + " (garantía)");
            if (!deposit.approved()) {
                // Never keep the rental charged when the deposit hold fails: the checkout is all or nothing.
                paymentGateway.refund(rental.reference());
                throw new PaymentDeclinedException(deposit.declineReason());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        reservation.addPayment(newPayment(reservation, reservation.getRentalAmount(), PaymentType.RENTAL,
                PaymentStatus.COMPLETED, rental.reference(), now));
        if (deposit != null) {
            reservation.addPayment(newPayment(reservation, reservation.getSecurityDeposit(),
                    PaymentType.SECURITY_DEPOSIT, PaymentStatus.AUTHORIZED, deposit.reference(), now));
        }
        log.info("Reservation {} paid by company {}", reservationId, actingCompanyId);
        return paymentRepository.saveAll(reservation.getPayments()).stream().map(paymentMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByReservation(Long reservationId) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
        boolean isParticipant = reservation.getRenter().getId().equals(actingCompanyId)
                || reservation.getEquipment().getOwner().getId().equals(actingCompanyId);
        if (!isParticipant) {
            throw new ForbiddenOperationException("You cannot see the payments of this reservation");
        }
        return paymentRepository.findByReservationId(reservationId).stream().map(paymentMapper::toResponse).toList();
    }

    /**
     * Moves the money when the reservation changes status. Called inside the status-change transaction,
     * so a gateway failure rolls the whole transition back.
     */
    public void settle(Reservation reservation, ReservationStatus newStatus) {
        boolean undone = newStatus == ReservationStatus.CANCELLED || newStatus == ReservationStatus.REJECTED;
        boolean finished = newStatus == ReservationStatus.COMPLETED;
        if (!undone && !finished) {
            return;
        }
        for (Payment payment : reservation.getPayments()) {
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                paymentGateway.release(payment.getProviderReference());
                payment.setStatus(PaymentStatus.REFUNDED);
            } else if (undone && payment.getStatus() == PaymentStatus.COMPLETED && payment.getType() == PaymentType.RENTAL) {
                paymentGateway.refund(payment.getProviderReference());
                payment.setStatus(PaymentStatus.REFUNDED);
            }
        }
    }

    public static ReservationPaymentStatus paymentStatusOf(Reservation reservation) {
        ReservationPaymentStatus result = ReservationPaymentStatus.UNPAID;
        for (Payment payment : reservation.getPayments()) {
            if (payment.getType() != PaymentType.RENTAL) continue;
            if (payment.getStatus() == PaymentStatus.COMPLETED) return ReservationPaymentStatus.PAID;
            if (payment.getStatus() == PaymentStatus.REFUNDED) result = ReservationPaymentStatus.REFUNDED;
        }
        return result;
    }

    private Payment newPayment(Reservation reservation, BigDecimal amount, PaymentType type, PaymentStatus status,
                               String reference, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(amount);
        payment.setCurrency(reservation.getCurrency());
        payment.setType(type);
        payment.setStatus(status);
        payment.setProviderReference(reference);
        payment.setPaidAt(paidAt);
        return payment;
    }
}
