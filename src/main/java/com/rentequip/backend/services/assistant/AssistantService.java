package com.rentequip.backend.services.assistant;

import com.rentequip.backend.dtos.request.AssistantChatRequest;
import com.rentequip.backend.dtos.request.AssistantChatRequest.ChatTurn;
import com.rentequip.backend.dtos.response.AssistantChatResponse;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.security.CurrentUser;
import com.rentequip.backend.services.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistantService {

    /** How much of the catalogue the model sees per question, closest to Lima first. */
    private static final int CATALOG_SIZE = 60;
    private static final int MAX_RECOMMENDATIONS = 3;

    private final EquipmentService equipmentService;
    private final ClaudeEquipmentAdvisor claudeAdvisor;
    private final CatalogKeywordRecommender keywordRecommender;
    private final CurrentUser currentUser;

    public AssistantChatResponse chat(AssistantChatRequest request) {
        List<ChatTurn> history = request.messages();
        ChatTurn last = history.get(history.size() - 1);
        if (!"user".equals(last.role())) {
            throw new InvalidOperationException("The last message must be the user's question");
        }

        List<EquipmentSummaryResponse> catalog = equipmentService.searchCatalog(null, null, null, null, null, null,
                null, null, null, currentUser.requireCompanyId(), 0, CATALOG_SIZE).content();

        return claudeAdvisor.advise(history, catalog)
                .map(answer -> toResponse(answer, catalog, "claude"))
                .orElseGet(() -> toResponse(keywordRecommender.recommend(last.content(), catalog), catalog,
                        "catalog-search"));
    }

    /** Keeps only ids that really are in the catalogue the recommender was given, in its order. */
    private AssistantChatResponse toResponse(AssistantAnswer answer, List<EquipmentSummaryResponse> catalog,
                                             String source) {
        Map<Long, EquipmentSummaryResponse> byId = catalog.stream()
                .collect(Collectors.toMap(EquipmentSummaryResponse::id, Function.identity()));
        List<EquipmentSummaryResponse> recommendations = answer.equipmentIds().stream()
                .distinct()
                .map(byId::get)
                .filter(item -> item != null)
                .limit(MAX_RECOMMENDATIONS)
                .toList();
        return new AssistantChatResponse(answer.reply(), recommendations, source);
    }
}
