package com.rentequip.backend.dtos.response;

/**
 * Aggregated rating of a single equipment, shown on its listing card.
 */
public record EquipmentRatingResponse(
        Long equipmentId,
        double averageRating,
        long reviewCount
) {
}
