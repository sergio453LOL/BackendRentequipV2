package com.rentequip.backend.dtos.response;

import java.util.List;

/**
 * @param source {@code claude} when the answer came from the language model, {@code catalog-search} when
 *               the local keyword recommender answered (no API key configured or the model was unavailable)
 */
public record AssistantChatResponse(
        String reply,
        List<EquipmentSummaryResponse> recommendations,
        String source
) {
}
