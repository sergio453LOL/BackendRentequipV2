package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.EquipmentCreateRequest;
import com.rentequip.backend.dtos.request.EquipmentUpdateRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.ReservationResponse;
import com.rentequip.backend.dtos.response.ReviewResponse;
import com.rentequip.backend.hateoas.EquipmentModelAssembler;
import com.rentequip.backend.services.EquipmentService;
import com.rentequip.backend.services.ReservationService;
import com.rentequip.backend.services.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
@Validated
public class EquipmentController {

    private final EquipmentModelAssembler equipmentAssembler;
    private final EquipmentService equipmentService;
    private final ReservationService reservationService;
    private final ReviewService reviewService;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<EntityModel<EquipmentResponse>> create(
            @Valid @RequestBody EquipmentCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        EquipmentResponse created = equipmentService.create(request);
        URI location = uriBuilder.path("/api/v1/equipment/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(equipmentAssembler.toModel(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity> findById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentAssembler.toModel(equipmentService.findById(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PatchMapping("/{id}")
    public ResponseEntity<EntityModel<EquipmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentUpdateRequest request) {
        return ResponseEntity.ok(equipmentAssembler.toModel(
                equipmentService.update(id, request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Catalogue search by proximity. Coordinates and radius drive the geospatial filter; category, city,
     * price and the date window narrow it further.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<EquipmentSummaryResponse>> search(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @RequestParam(defaultValue = "25") @DecimalMin("0.1") @DecimalMax("1000.0") double radiusKm,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal maxDailyRate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long excludeOwnerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(equipmentService.search(latitude, longitude, radiusKm, categoryId, city,
                maxDailyRate, startDate, endDate, excludeOwnerId, page, size));
    }

    @GetMapping("/{id}/reservations")
    public ResponseEntity<PageResponse<ReservationResponse>> findReservations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startDate"));
        return ResponseEntity.ok(reservationService.findByEquipment(id, pageable));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> findReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.findByEquipment(id, pageable));
    }
}
