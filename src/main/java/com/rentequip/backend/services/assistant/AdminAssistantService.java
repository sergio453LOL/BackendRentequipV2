package com.rentequip.backend.services.assistant;

import com.rentequip.backend.dtos.request.AssistantChatRequest;
import com.rentequip.backend.dtos.request.AssistantChatRequest.ChatTurn;
import com.rentequip.backend.dtos.response.AdminAssistantResponse;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * The admin copilot: a platform admin asks about the platform in their own words and the model answers
 * from the live figures. It shares the catalogue assistant's usage limits (its own budget per admin, the
 * same daily cap); past them, or with no model configured, the answer is the summary of the main figures.
 */
@Service
@RequiredArgsConstructor
public class AdminAssistantService {

    /** Quota key of the copilot, apart from the same admin's questions to the catalogue assistant. */
    private static final String QUOTA_SCOPE = "admin-copilot";
    private static final int MODEL_HISTORY_TURNS = 8;

    private final PlatformSnapshot snapshot;
    private final PlatformAnalyst analyst;
    private final AssistantQuota quota;
    private final CurrentUser currentUser;

    public AdminAssistantResponse chat(AssistantChatRequest request) {
        List<ChatTurn> history = request.messages();
        if (!"user".equals(history.get(history.size() - 1).role())) {
            throw new InvalidOperationException("The last message must be the user's question");
        }
        PlatformSnapshot.Facts facts = snapshot.capture();
        Instant asOf = Instant.now();
        if (analyst.isEnabled() && quota.tryAcquire(currentUser.requireUserId(), QUOTA_SCOPE)) {
            var answer = analyst.answer(recentTurns(history), facts.text());
            if (answer.isPresent()) {
                return new AdminAssistantResponse(answer.get().reply(), answer.get().source(), asOf);
            }
        }
        return new AdminAssistantResponse(facts.summary(), "summary", asOf);
    }

    /** The last turns, starting with a question as the model APIs expect. */
    private static List<ChatTurn> recentTurns(List<ChatTurn> history) {
        List<ChatTurn> recent = history.subList(Math.max(0, history.size() - MODEL_HISTORY_TURNS), history.size());
        int firstQuestion = 0;
        while (!"user".equals(recent.get(firstQuestion).role())) firstQuestion++;
        return recent.subList(firstQuestion, recent.size());
    }
}
