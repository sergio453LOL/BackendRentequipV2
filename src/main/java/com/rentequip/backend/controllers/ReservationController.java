package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.PaymentRequest;
import com.rentequip.backend.dtos.request.ReservationCreateRequest;
import com.rentequip.backend.dtos.request.ReservationStatusUpdateRequest;
import com.rentequip.backend.dtos.response.AvailabilityResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.PaymentResponse;
import com.rentequip.backend.dtos.response.ReservationResponse;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.hateoas.ReservationModelAssembler;
import com.rentequip.backend.services.PaymentService;
import com.rentequip.backend.services.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Validated
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationModelAssembler reservationAssembler;
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<EntityModel<ReservationResponse>> create(
            @Valid @RequestBody ReservationCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        ReservationResponse created = reservationService.create(request);
        URI location = uriBuilder.path("/api/v1/reservations/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(reservationAssembler.toModel(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ReservationResponse>> findById(
            @PathVariable Long id) {
        return ResponseEntity.ok(reservationAssembler.toModel(
                reservationService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReservationResponse>> findMine(
            @RequestParam(defaultValue = "RENTER") ReservationRole role,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(
                reservationService.findForCompany(role, status, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<EntityModel<ReservationResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ReservationStatusUpdateRequest request) {
        return ResponseEntity.ok(reservationAssembler.toModel(
                reservationService.updateStatus(id, request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/{id}/confirmation")
    public ResponseEntity<EntityModel<ReservationResponse>> confirm(
            @PathVariable Long id) {
        return ResponseEntity.ok(reservationAssembler.toModel(
                reservationService.confirm(id)));
    }

    @PostMapping("/{id}/cancellation")
    public ResponseEntity<EntityModel<ReservationResponse>> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(reservationAssembler.toModel(
                reservationService.cancel(id, reason)));
    }

    /**
     * Checkout: captures the rental and holds the security deposit using a client-side payment token.
     */
    @PostMapping("/{id}/payments")
    public ResponseEntity<List<PaymentResponse>> pay(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(id, request));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PaymentResponse>> findPayments(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.findByReservation(id));
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @RequestParam @NotNull Long equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reservationService.checkAvailability(equipmentId, startDate, endDate));
    }
}
