package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.HandoverStage;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HandoverPhotoResponse(
        Long id,
        Long reservationId,
        HandoverStage stage,
        String imageUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        String note,
        Long uploadedByCompanyId,
        String uploadedByCompanyName,
        LocalDateTime createdAt
) {
}
