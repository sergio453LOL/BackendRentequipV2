package com.rentequip.backend.dtos.response;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long reservationId,
        Long equipmentId,
        Long reviewerCompanyId,
        String reviewerCompanyName,
        Integer rating,
        Integer conditionRating,
        String comment,
        LocalDateTime createdAt
) {
}
