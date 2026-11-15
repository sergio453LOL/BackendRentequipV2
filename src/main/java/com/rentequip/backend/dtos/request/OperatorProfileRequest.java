package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Who runs the machine when the rate includes an operator: the renter sees it before booking. Every field
 * is optional; a blank name removes the sheet.
 */
public record OperatorProfileRequest(

        @Size(max = 120)
        String name,

        /** Driving or heavy-machinery licence, e.g. "A-IIIc" or the MTC certificate number. */
        @Size(max = 60)
        String license,

        @Min(0) @Max(60)
        Integer experienceYears,

        /** Free text, one certification per line: SCTR, safety course, brand training... */
        @Size(max = 500)
        String certifications
) {
}
