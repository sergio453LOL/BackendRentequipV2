package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.EquipmentStatus;

import java.math.BigDecimal;

/**
 * Lightweight projection used by the search results list and the map pins.
 */
public record EquipmentSummaryResponse(
        Long id,
        String name,
        String brand,
        String model,
        BigDecimal dailyRate,
        String currency,
        EquipmentStatus status,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        String primaryImageUrl,
        Long ownerCompanyId,
        String ownerCompanyName,
        boolean operatorIncluded
) {
}
