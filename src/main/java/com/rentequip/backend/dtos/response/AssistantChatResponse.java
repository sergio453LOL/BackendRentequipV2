package com.rentequip.backend.dtos.response;

import java.util.List;

/**
 * @param source {@code gemini} or {@code claude} when a language model answered, {@code catalog-search} when
 *               the local keyword recommender answered (no API key, a usage limit reached or the model was
 *               unavailable), {@code guardrail} when the question was answered locally because it was a
 *               greeting, spam, off topic or an attempt to change the assistant's rules
 */
public record AssistantChatResponse(
        String reply,
        List<EquipmentSummaryResponse> recommendations,
        String source
) {
}
