package com.rentequip.backend.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Conversation with the catalogue assistant. The client sends the whole history because the API is
 * stateless; the last turn must be the user's question.
 */
public record AssistantChatRequest(
        @NotEmpty @Size(max = 20)
        List<@Valid @NotNull ChatTurn> messages
) {
    public record ChatTurn(
            @NotNull @Pattern(regexp = "user|assistant")
            String role,
            @NotBlank @Size(max = 1000)
            String content
    ) {
    }
}
