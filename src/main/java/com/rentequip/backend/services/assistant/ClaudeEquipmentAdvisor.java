package com.rentequip.backend.services.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.rentequip.backend.dtos.request.AssistantChatRequest.ChatTurn;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Asks Claude to pick equipment from the catalogue for a request written in natural language. The model
 * only sees the catalogue the backend hands it and must answer with ids from that list, which the caller
 * validates again before returning anything to the user.
 */
@Component
public class ClaudeEquipmentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeEquipmentAdvisor.class);

    private static final String INSTRUCTIONS = """
            Eres el asistente de RentEquip, un marketplace B2B peruano de alquiler de maquinaria de construcción.
            Ayudas a empresas constructoras a elegir qué equipo alquilar para su obra.

            Reglas:
            - Recomienda solo equipos del catálogo que aparece abajo, usando sus ids exactos. Nunca inventes equipos.
            - Recomienda como máximo 3 equipos, del más al menos adecuado, y explica en una frase por qué sirve cada uno.
            - Si el usuario menciona una ciudad, prioriza los equipos de esa ciudad.
            - Si ningún equipo del catálogo sirve para lo que pide, dilo con claridad, devuelve la lista de ids vacía y
              sugiere qué tipo de máquina debería buscar.
            - Si la pregunta no tiene que ver con maquinaria o con el uso de RentEquip, responde brevemente que solo
              puedes ayudar con eso.
            - Responde en español, en tono profesional y cercano, en menos de 120 palabras. Muestra precios en la moneda
              del catálogo. No uses markdown.

            Catálogo disponible (id | nombre | marca y modelo | ciudad | tarifa diaria):
            """;

    private final AnthropicClient client;
    private final String model;

    public ClaudeEquipmentAdvisor(@Value("${rentequip.assistant.api-key}") String apiKey,
                                  @Value("${rentequip.assistant.model}") String model) {
        this.client = apiKey == null || apiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** Empty when the model is not configured, refused, or failed; the caller then falls back. */
    public Optional<AssistantAnswer> advise(List<ChatTurn> history, List<EquipmentSummaryResponse> catalog) {
        if (client == null) {
            return Optional.empty();
        }
        StructuredMessageCreateParams.Builder<ModelAnswer> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4000L)
                .system(INSTRUCTIONS + describe(catalog))
                .outputConfig(ModelAnswer.class)
                // Server-side fallback: if a safety classifier declines, the API retries on another model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        for (ChatTurn turn : history) {
            if ("assistant".equals(turn.role())) {
                builder.addAssistantMessage(turn.content());
            } else {
                builder.addUserMessage(turn.content());
            }
        }

        try {
            StructuredMessage<ModelAnswer> response = client.messages().create(builder.build());
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.warn("Assistant request refused by the model");
                return Optional.empty();
            }
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .map(answer -> new AssistantAnswer(answer.reply(),
                            answer.equipmentIds() == null ? List.of() : answer.equipmentIds()));
        } catch (AnthropicException exception) {
            log.warn("Assistant model unavailable, falling back to catalogue search: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String describe(List<EquipmentSummaryResponse> catalog) {
        if (catalog.isEmpty()) {
            return "(el catálogo está vacío)";
        }
        return catalog.stream()
                .map(item -> "%d | %s | %s %s | %s | %s %s".formatted(item.id(), item.name(), item.brand(),
                        item.model(), item.city(), item.dailyRate(), item.currency()))
                .collect(Collectors.joining("\n"));
    }

    record ModelAnswer(
            @JsonPropertyDescription("Respuesta para el usuario, en español")
            String reply,
            @JsonPropertyDescription("Ids de los equipos recomendados, tomados del catálogo; vacío si ninguno sirve")
            List<Long> equipmentIds
    ) {
    }
}
