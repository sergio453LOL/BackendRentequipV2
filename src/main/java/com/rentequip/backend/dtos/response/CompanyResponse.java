package com.rentequip.backend.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String taxId,
        String email,
        String phone,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        String logoUrl,
        String description,
        boolean active,
        LocalDateTime createdAt
) {
}
