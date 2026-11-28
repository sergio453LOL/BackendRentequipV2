package com.rentequip.backend.dtos.response;

import java.time.Instant;

/**
 * @param source {@code gemini} or {@code claude} when a language model answered, {@code summary} when the
 *               backend answered with the main figures (no model configured, usage limit reached or the
 *               model failed)
 * @param asOf   when the figures behind the answer were read
 */
public record AdminAssistantResponse(String reply, String source, Instant asOf) {
}
