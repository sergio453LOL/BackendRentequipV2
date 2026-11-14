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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistantService {

    /** Source of answers the guardrails gave locally, without calling any model. */
    static final String GUARDRAIL = "guardrail";

    /** How much of the catalogue is searched per question, closest to Lima first. */
    private static final int CATALOG_SIZE = 60;
    /** How many catalogue items the model sees: the best keyword matches, not the whole catalogue. */
    private static final int MODEL_CATALOG_SIZE = 15;
    /** How many turns of the conversation the model sees; older ones cost tokens and rarely matter. */
    private static final int MODEL_HISTORY_TURNS = 6;
    private static final int MAX_RECOMMENDATIONS = 3;

    private final EquipmentService equipmentService;
    private final GeminiEquipmentAdvisor geminiAdvisor;
    private final ClaudeEquipmentAdvisor claudeAdvisor;
    private final CatalogKeywordRecommender keywordRecommender;
    private final AssistantGuard guard;
    private final AssistantQuota quota;
    private final AssistantAnswerCache cache;
    private final CurrentUser currentUser;

    public AssistantChatResponse chat(AssistantChatRequest request) {
        List<ChatTurn> history = request.messages();
        ChatTurn last = history.get(history.size() - 1);
        if (!"user".equals(last.role())) {
            throw new InvalidOperationException("The last message must be the user's question");
        }

        // Noise, small talk and prompt injection are answered here, before even reading the catalogue.
        Optional<AssistantAnswer> screened = guard.screen(last.content());
        if (screened.isPresent()) {
            return toResponse(screened.get(), List.of(), GUARDRAIL);
        }

        Long companyId = currentUser.requireCompanyId();
        List<EquipmentSummaryResponse> catalog = equipmentService.searchCatalog(null, null, null, null, null, null,
                null, null, null, companyId, 0, CATALOG_SIZE).content();
        if (!guard.isOnTopic(history, catalog)) {
            return toResponse(new AssistantAnswer(AssistantGuard.OFF_TOPIC, List.of()), List.of(), GUARDRAIL);
        }

        boolean opening = history.size() == 1;
        if (opening) {
            Optional<AssistantAnswerCache.CachedAnswer> cached = cache.get(companyId, last.content());
            if (cached.isPresent()) {
                return toResponse(cached.get().answer(), catalog, cached.get().source());
            }
        }

        Supplier<AssistantChatResponse> localSearch = () ->
                toResponse(keywordRecommender.recommend(last.content(), catalog), catalog, "catalog-search");
        boolean modelConfigured = geminiAdvisor.isEnabled() || claudeAdvisor.isEnabled();
        if (!modelConfigured || !quota.tryAcquire(currentUser.requireUserId())) {
            return localSearch.get();
        }

        List<ChatTurn> recentHistory = recentTurns(history);
        List<EquipmentSummaryResponse> candidates = candidatesFor(recentHistory, catalog);
        // Gemini first (cheapest), then Claude, then the offline search; each one only if configured.
        Optional<AssistantChatResponse> modelAnswer = geminiAdvisor.advise(recentHistory, candidates)
                .map(answer -> remember(opening, companyId, last, answer, "gemini"))
                .or(() -> claudeAdvisor.advise(recentHistory, candidates)
                        .map(answer -> remember(opening, companyId, last, answer, "claude")))
                .map(answer -> toResponse(answer.answer(), catalog, answer.source()));
        return modelAnswer.orElseGet(localSearch);
    }

    private AssistantAnswerCache.CachedAnswer remember(boolean opening, Long companyId, ChatTurn question,
                                                       AssistantAnswer answer, String source) {
        if (opening) {
            cache.put(companyId, question.content(), answer, source);
        }
        return new AssistantAnswerCache.CachedAnswer(answer, source);
    }

    /** The last turns of the conversation, starting with a question as the model APIs expect. */
    private static List<ChatTurn> recentTurns(List<ChatTurn> history) {
        List<ChatTurn> recent = history.subList(Math.max(0, history.size() - MODEL_HISTORY_TURNS), history.size());
        int firstQuestion = 0;
        while (!"user".equals(recent.get(firstQuestion).role())) firstQuestion++;
        return recent.subList(firstQuestion, recent.size());
    }

    /** The items that match the conversation; the first ones of the catalogue when nothing matches. */
    private List<EquipmentSummaryResponse> candidatesFor(List<ChatTurn> history, List<EquipmentSummaryResponse> catalog) {
        String conversation = history.stream().map(ChatTurn::content).collect(Collectors.joining(" "));
        List<EquipmentSummaryResponse> matches = keywordRecommender.rank(conversation, catalog, MODEL_CATALOG_SIZE);
        return matches.isEmpty() ? catalog.subList(0, Math.min(MODEL_CATALOG_SIZE, catalog.size())) : matches;
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
