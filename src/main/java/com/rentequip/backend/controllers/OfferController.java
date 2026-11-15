package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.OfferCreateRequest;
import com.rentequip.backend.dtos.request.OfferReplyRequest;
import com.rentequip.backend.dtos.response.OfferResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.services.OfferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Price negotiation. The renter opens it with a counteroffer; each answer is an action on the offer
 * (acceptance, rejection, counter, cancellation), like the reservation endpoints.
 */
@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
@Validated
public class OfferController {

    private final OfferService offerService;

    @PostMapping
    public ResponseEntity<OfferResponse> create(@Valid @RequestBody OfferCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(offerService.create(request));
    }

    /** RENTER: the offers this company sent. OWNER: the offers received on its machines. */
    @GetMapping
    public ResponseEntity<PageResponse<OfferResponse>> findMine(
            @RequestParam(defaultValue = "RENTER") ReservationRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(offerService.findMine(role, page, size));
    }

    @PostMapping("/{id}/acceptance")
    public ResponseEntity<OfferResponse> accept(@PathVariable Long id, @Valid @RequestBody(required = false) OfferReplyRequest reply) {
        return ResponseEntity.ok(offerService.accept(id, reply));
    }

    @PostMapping("/{id}/rejection")
    public ResponseEntity<OfferResponse> reject(@PathVariable Long id, @Valid @RequestBody(required = false) OfferReplyRequest reply) {
        return ResponseEntity.ok(offerService.reject(id, reply));
    }

    @PostMapping("/{id}/counter")
    public ResponseEntity<OfferResponse> counter(@PathVariable Long id, @Valid @RequestBody OfferReplyRequest reply) {
        return ResponseEntity.ok(offerService.counter(id, reply));
    }

    @PostMapping("/{id}/cancellation")
    public ResponseEntity<OfferResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.cancel(id));
    }
}
