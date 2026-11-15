package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.TwoFactorChannel;

/**
 * A code was sent: the client shows where (masked) and posts the code back with the challenge id.
 */
public record TwoFactorChallengeResponse(
        String challengeId,
        TwoFactorChannel channel,
        String destination,
        long expiresInSeconds
) {
}
