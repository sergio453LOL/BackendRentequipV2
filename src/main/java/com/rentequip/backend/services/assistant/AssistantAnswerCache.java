package com.rentequip.backend.services.assistant;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers the model's answer to an opening question for a while, so the same question asked again (the
 * suggestion buttons, or someone pressing send repeatedly) costs no tokens. Keyed by company, because each
 * company sees a catalogue without its own equipment. The caller validates the ids again against the live
 * catalogue, so an item rented or removed in the meantime is never recommended.
 */
@Component
public class AssistantAnswerCache {

    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 500;

    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public AssistantAnswerCache() {
        this(Clock.systemUTC());
    }

    AssistantAnswerCache(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<CachedAnswer> get(Long companyId, String question) {
        String key = key(companyId, question);
        Entry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt().isBefore(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.answer());
    }

    public synchronized void put(Long companyId, String question, AssistantAnswer answer, String source) {
        entries.put(key(companyId, question),
                new Entry(new CachedAnswer(answer, source), clock.instant().plus(TIME_TO_LIVE)));
    }

    /** Case, accents, punctuation and extra spaces do not make a question different. */
    private static String key(Long companyId, String question) {
        return companyId + ":" + CatalogKeywordRecommender.normalize(question).replaceAll("[^a-z0-9]+", " ").strip();
    }

    public record CachedAnswer(AssistantAnswer answer, String source) {
    }

    private record Entry(CachedAnswer answer, Instant expiresAt) {
    }
}
