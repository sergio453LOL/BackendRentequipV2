package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.EquipmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
// Formato de respuestas y DTOs actualizado

public record EquipmentResponse(
        Long id,
        String name,
        String description,
        String brand,
        String model,
        Integer manufactureYear,
        String serialNumber,
        String specifications,
        BigDecimal dailyRate,
        BigDecimal weeklyRate,
        BigDecimal securityDeposit,
        String currency,
        EquipmentStatus status,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> imageUrls,
        Set<String> categories,
        Long ownerCompanyId,
        String ownerCompanyName,
        LocalDateTime createdAt
) {
}
