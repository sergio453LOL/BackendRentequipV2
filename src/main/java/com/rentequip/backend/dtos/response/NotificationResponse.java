package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        /** Web path that opens the related screen; null when there is none. */
        String link,
        boolean read,
        LocalDateTime createdAt
) {
}
