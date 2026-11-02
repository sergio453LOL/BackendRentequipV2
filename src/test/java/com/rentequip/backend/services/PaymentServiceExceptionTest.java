package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.PaymentRequest;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.PaymentDeclinedException;
import com.rentequip.backend.exceptions.PaymentProviderException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.PaymentMapper;
import com.rentequip.backend.payments.PaymentGateway;
import com.rentequip.backend.payments.PaymentGateway.GatewayResult;
import com.rentequip.backend.payments.PaymentGateway.PaymentSource;
import com.rentequip.backend.payments.PaymentGateway.PreparedSource;
import com.rentequip.backend.repositories.PaymentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the checkout's failure paths. The gateway and the repositories are Mockito mocks, so each
 * test decides what Stripe "answers" and checks which exception the service throws and what it undoes.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceExceptionTest {

    private static final long RESERVATION_ID = 7L;
    private static final long RENTER_ID = 1L;
    private static final String TOKEN = "pm_card_visa";
    private static final PaymentSource SOURCE = new PaymentSource(TOKEN, "cus_test");

    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private CurrentUser currentUser;

    @InjectMocks private PaymentService paymentService;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        Company renter = company(RENTER_ID);
        Equipment equipment = new Equipment();
        equipment.setName("Excavadora CAT 320");
        equipment.setOwner(company(2L));

        reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setRenter(renter);
        reservation.setEquipment(equipment);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setRentalAmount(new BigDecimal("1400.00"));
        reservation.setSecurityDeposit(new BigDecimal("500.00"));
        reservation.setCurrency("PEN");
    }

    @Test
    void throwsNotFoundWhenTheReservationDoesNotExist() {
        // Arrange
        when(currentUser.requireCompanyId()).thenReturn(RENTER_ID);
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.empty());

        // Act + Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(paymentGateway, never()).prepare(anyString(), anyString());
    }

    @Test
    void throwsForbiddenWhenAnotherCompanyTriesToPay() {
        when(currentUser.requireCompanyId()).thenReturn(99L);
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenOperationException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));
        verify(paymentGateway, never()).prepare(anyString(), anyString());
    }

    @Test
    void throwsInvalidOperationWhenTheReservationIsCancelled() {
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(currentUser.requireCompanyId()).thenReturn(RENTER_ID);
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        assertThrows(InvalidOperationException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));
    }

    @Test
    void throwsPaymentDeclinedWithTheBankReasonWhenTheRentalIsDeclined() {
        givenAPayableReservationWithAPreparedCard();
        when(paymentGateway.charge(eq(SOURCE), any(), eq("PEN"), anyString()))
                .thenReturn(GatewayResult.declined("La tarjeta no tiene fondos suficientes."));

        PaymentDeclinedException exception = assertThrows(PaymentDeclinedException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));

        assertThat(exception.getMessage()).isEqualTo("La tarjeta no tiene fondos suficientes.");
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(exception.getErrorCode()).isEqualTo("PAYMENT_DECLINED");
        verify(paymentGateway, never()).authorize(any(), any(), anyString(), anyString());
    }

    @Test
    void refundsTheRentalWhenTheDepositHoldIsDeclined() {
        givenAPayableReservationWithAPreparedCard();
        when(paymentGateway.charge(eq(SOURCE), any(), eq("PEN"), anyString()))
                .thenReturn(GatewayResult.approved("pi_rental"));
        when(paymentGateway.authorize(eq(SOURCE), any(), eq("PEN"), anyString()))
                .thenReturn(GatewayResult.declined("La tarjeta fue rechazada por el banco emisor."));

        assertThrows(PaymentDeclinedException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));

        // The checkout is all or nothing: the rental that was already charged goes back to the card.
        verify(paymentGateway).refund("pi_rental");
    }

    @Test
    void refundsTheRentalWhenStripeFailsDuringTheDepositHold() {
        givenAPayableReservationWithAPreparedCard();
        when(paymentGateway.charge(eq(SOURCE), any(), eq("PEN"), anyString()))
                .thenReturn(GatewayResult.approved("pi_rental"));
        when(paymentGateway.authorize(eq(SOURCE), any(), eq("PEN"), anyString()))
                .thenThrow(new PaymentProviderException("Stripe no respondió"));

        PaymentProviderException exception = assertThrows(PaymentProviderException.class,
                () -> paymentService.pay(RESERVATION_ID, new PaymentRequest(TOKEN)));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(paymentGateway).refund("pi_rental");
    }

    private void givenAPayableReservationWithAPreparedCard() {
        when(currentUser.requireCompanyId()).thenReturn(RENTER_ID);
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(paymentGateway.prepare(eq(TOKEN), anyString())).thenReturn(PreparedSource.ready(SOURCE));
    }

    private static Company company(long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Empresa " + id);
        return company;
    }
}
