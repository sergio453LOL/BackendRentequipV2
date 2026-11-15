package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a signed-in user may change about themselves. A null field keeps the current value; an empty
 * phone or avatar removes it. Role, email and company are not editable from here.
 */
public record ProfileUpdateRequest(
        @Size(min = 1, max = 80)
        String firstName,

        @Size(min = 1, max = 80)
        String lastName,

        @Size(max = 20)
        String phone,

        /** Only http(s) URLs, as returned by the upload endpoint. */
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Avatar must be an http(s) URL")
        String avatarUrl
) {
}
