package com.rentequip.backend.services.assistant;

import java.util.List;

/** What a recommender decided: the text for the user and the catalogue items it recommends. */
public record AssistantAnswer(String reply, List<Long> equipmentIds) {
}
