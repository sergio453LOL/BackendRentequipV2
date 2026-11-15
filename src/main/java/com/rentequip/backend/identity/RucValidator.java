package com.rentequip.backend.identity;

/**
 * Offline check of a Peruvian RUC: 11 digits, a valid prefix and SUNAT's modulo-11 check digit. It catches
 * typos before any request to SUNAT, and works even when no lookup API is configured.
 */
public final class RucValidator {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucValidator() {
    }

    public static boolean isValid(String ruc) {
        if (ruc == null || !ruc.matches("(10|15|16|17|20)\\d{9}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (ruc.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 11 - sum % 11;
        if (check == 10) check = 0;
        if (check == 11) check = 1;
        return check == ruc.charAt(10) - '0';
    }

    /** RUC 20: legal entity. RUC 10: natural person with a business. 15/16/17: other registrations. */
    public static RucType typeOf(String ruc) {
        return switch (ruc.substring(0, 2)) {
            case "20" -> RucType.LEGAL_ENTITY;
            case "10" -> RucType.NATURAL_PERSON;
            default -> RucType.OTHER;
        };
    }

    public enum RucType {
        LEGAL_ENTITY,
        NATURAL_PERSON,
        OTHER
    }
}
