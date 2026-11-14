package com.rentequip.backend.services.assistant;

import com.rentequip.backend.dtos.request.AssistantChatRequest.ChatTurn;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Same job as {@link ClaudeEquipmentAdvisor}, on Google's Gemini API (no SDK: one JSON POST to
 * generateContent). It uses the Flash-Lite model, the cheapest one, and asks for a JSON answer that follows
 * a schema, so the reply and the recommended ids come back already separated.
 */
@Component
public class GeminiEquipmentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(GeminiEquipmentAdvisor.class);

    private static final Map<String, Object> ANSWER_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "reply", Map.of("type", "STRING", "description", "Respuesta para el usuario, en español"),
                    "equipmentIds", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER"),
                            "description", "Ids de los equipos recomendados, tomados del catálogo; vacío si ninguno sirve")),
            "required", List.of("reply", "equipmentIds"));

    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final String model;
    private final boolean configured;

    public GeminiEquipmentAdvisor(@Value("${rentequip.assistant.gemini.api-key}") String apiKey,
                                  @Value("${rentequip.assistant.gemini.model}") String model,
                                  JsonMapper jsonMapper) {
        this.configured = apiKey != null && !apiKey.isBlank();
        this.model = model;
        this.jsonMapper = jsonMapper;
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("X-goog-api-key", configured ? apiKey.trim() : "")
                .build();
    }

    public boolean isEnabled() {
        return configured;
    }

    /** Empty when the key is missing, Gemini fails or blocks the answer; the caller then falls back. */
    public Optional<AssistantAnswer> advise(List<ChatTurn> history, List<EquipmentSummaryResponse> catalog) {
        if (!configured) {
            return Optional.empty();
        }
        // Gemini calls the assistant's turns "model".
        List<Map<String, Object>> contents = history.stream()
                .map(turn -> Map.<String, Object>of(
                        "role", "assistant".equals(turn.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", turn.content()))))
                .toList();
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text",
                        ClaudeEquipmentAdvisor.INSTRUCTIONS + ClaudeEquipmentAdvisor.describe(catalog)))),
                "contents", contents,
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", ANSWER_SCHEMA,
                        // An 80-word reply plus three ids fits in ~250 tokens; the cap stops runaway answers.
                        "maxOutputTokens", 400));

        try {
            GeminiResponse response = client.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            Optional<String> text = Optional.ofNullable(response)
                    .map(GeminiResponse::candidates)
                    .flatMap(candidates -> candidates.stream().findFirst())
                    .map(Candidate::content)
                    .map(Content::parts)
                    .flatMap(parts -> parts.stream().map(Part::text).filter(part -> part != null).findFirst());
            if (text.isEmpty()) {
                log.warn("Gemini returned no answer (blocked or empty)");
                return Optional.empty();
            }
            ModelAnswer answer = jsonMapper.readValue(text.get(), ModelAnswer.class);
            return Optional.of(new AssistantAnswer(answer.reply(),
                    answer.equipmentIds() == null ? List.of() : answer.equipmentIds()));
        } catch (RestClientResponseException exception) {
            log.warn("Gemini refused the request: HTTP {} {}", exception.getStatusCode().value(),
                    exception.getResponseBodyAsString());
            return Optional.empty();
        } catch (RestClientException | JacksonException exception) {
            log.warn("Gemini unavailable, falling back: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    record GeminiResponse(List<Candidate> candidates) {
    }

    record Candidate(Content content) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }

    record ModelAnswer(String reply, List<Long> equipmentIds) {
    }
}
