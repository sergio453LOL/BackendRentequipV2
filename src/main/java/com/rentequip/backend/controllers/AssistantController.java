package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.AssistantChatRequest;
import com.rentequip.backend.dtos.response.AssistantChatResponse;
import com.rentequip.backend.services.assistant.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /** Natural-language question about which machine to rent; answers with catalogue recommendations. */
    @PostMapping("/chat")
    public ResponseEntity<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        return ResponseEntity.ok(assistantService.chat(request));
    }
}
