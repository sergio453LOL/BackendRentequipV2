package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.response.HandoverPhotoResponse;
import com.rentequip.backend.enums.HandoverStage;
import com.rentequip.backend.services.HandoverPhotoService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/** Condition photos of the equipment taken at delivery and return, used by the mobile app's camera. */
@RestController
@RequestMapping("/api/v1/reservations/{reservationId}/handover-photos")
@RequiredArgsConstructor
@Validated
public class HandoverPhotoController {

    private final HandoverPhotoService handoverPhotoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HandoverPhotoResponse> upload(
            @PathVariable Long reservationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam HandoverStage stage,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @RequestParam(required = false) @Size(max = 255) String note) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(handoverPhotoService.upload(reservationId, file, stage, latitude, longitude, note));
    }

    @GetMapping
    public ResponseEntity<List<HandoverPhotoResponse>> findAll(@PathVariable Long reservationId) {
        return ResponseEntity.ok(handoverPhotoService.findByReservation(reservationId));
    }
}
