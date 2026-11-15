package com.rentequip.backend.dtos.response;

/** The operator sheet shown on the equipment page; absent when no operator was described. */
public record OperatorProfileResponse(
        String name,
        String license,
        Integer experienceYears,
        String certifications
) {
}
