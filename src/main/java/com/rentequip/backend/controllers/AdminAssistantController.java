package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.AssistantChatRequest;
import com.rentequip.backend.dtos.response.AdminAssistantResponse;
import com.rentequip.backend.security.PlatformAdmins;
import com.rentequip.backend.services.assistant.AdminAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/assistant")
@RequiredArgsConstructor
public class AdminAssistantController {

    private final AdminAssistantService adminAssistantService;

    /** Question about the platform (companies, catalogue, reservations, money), answered from live figures. */
    @PostMapping("/chat")
    @PreAuthorize(PlatformAdmins.EXPRESSION)
    public ResponseEntity<AdminAssistantResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        return ResponseEntity.ok(adminAssistantService.chat(request));
    }
}
