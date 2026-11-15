package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.EquipmentSaleRequest;
import com.rentequip.backend.dtos.request.PaymentRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.PurchaseQuoteResponse;
import com.rentequip.backend.dtos.response.PurchaseRequestResponse;
import com.rentequip.backend.enums.ReservationRole;
import com.rentequip.backend.services.PurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rent with an option to buy: sale terms on a machine, purchase requests, and paying for one. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/equipment/{id}/sale")
    public ResponseEntity<EquipmentResponse> updateSaleTerms(@PathVariable Long id,
                                                             @Valid @RequestBody EquipmentSaleRequest request) {
        return ResponseEntity.ok(purchaseService.updateSaleTerms(id, request));
    }

    @GetMapping("/reservations/{id}/purchase-quote")
    public ResponseEntity<PurchaseQuoteResponse> quote(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.quote(id));
    }

    @PostMapping("/reservations/{id}/purchase-requests")
    public ResponseEntity<PurchaseRequestResponse> create(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.create(id));
    }

    @GetMapping("/purchase-requests")
    public ResponseEntity<PageResponse<PurchaseRequestResponse>> findMine(
            @RequestParam(defaultValue = "RENTER") ReservationRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(purchaseService.findMine(role, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/purchase-requests/{id}/acceptance")
    public ResponseEntity<PurchaseRequestResponse> accept(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.accept(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/purchase-requests/{id}/rejection")
    public ResponseEntity<PurchaseRequestResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.reject(id));
    }

    @PostMapping("/purchase-requests/{id}/cancellation")
    public ResponseEntity<PurchaseRequestResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.cancel(id));
    }

    @PostMapping("/purchase-requests/{id}/payment")
    public ResponseEntity<PurchaseRequestResponse> pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(purchaseService.pay(id, request));
    }
}
