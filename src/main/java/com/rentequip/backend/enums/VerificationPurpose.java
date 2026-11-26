package com.rentequip.backend.enums;

/**
 * What a verification code unlocks: finishing a sign-in, turning the two-step verification on, or (ACCOUNT)
 * a change that normally asks for the current password, for accounts that have none (created with Google).
 * The column holds at most 10 characters.
 */
public enum VerificationPurpose {
    LOGIN,
    SETUP,
    ACCOUNT
}
