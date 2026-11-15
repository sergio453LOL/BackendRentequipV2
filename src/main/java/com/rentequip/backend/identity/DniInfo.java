package com.rentequip.backend.identity;

/** RENIEC's record of a national ID (DNI): the person's names, used to fill in the registration form. */
public record DniInfo(
        String dni,
        String firstNames,
        String lastNames,
        String fullName
) {
}
