package com.rentequip.backend.services.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How many questions may reach the language model: a burst limit per minute and an hourly limit per user,
 * plus a daily limit for the whole platform that protects the API key's quota. Past a limit the assistant
 * keeps answering with the local catalogue search, so nobody is blocked and no tokens are spent. Counters live
 * in memory: a restart resets them, which is fine for a single backend instance.
 */
@Component
public class AssistantQuota {

    private static final Logger log = LoggerFactory.getLogger(AssistantQuota.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final int perMinute;
    private final int perHour;
    private final int perDay;
    private final Clock clock;

    /** Keyed by user, or by user and demo visit: the shared demo account gets one budget per visitor. */
    private final Map<String, Deque<Instant>> callsByUser = new ConcurrentHashMap<>();
    private LocalDate today;
    private int callsToday;

    @Autowired
    public AssistantQuota(@Value("${rentequip.assistant.limits.per-user-per-minute}") int perMinute,
                          @Value("${rentequip.assistant.limits.per-user-per-hour}") int perHour,
                          @Value("${rentequip.assistant.limits.global-per-day}") int perDay) {
        this(perMinute, perHour, perDay, Clock.system(LIMA));
    }

    AssistantQuota(int perMinute, int perHour, int perDay, Clock clock) {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.perDay = perDay;
        this.clock = clock;
    }

    /** Takes one model call from the user's and the platform's budget; false when either is used up. */
    public boolean tryAcquire(Long userId) {
        return tryAcquire(userId, null);
    }

    /**
     * Same, for a visit of the shared demo account ({@code demoSession} from its token): every visitor gets
     * the per-user budget of their own, so an auditorium trying the demo at once does not use up one
     * budget in a minute. The daily platform limit still caps the total cost.
     */
    public boolean tryAcquire(Long userId, String demoSession) {
        Instant now = clock.instant();
        String key = demoSession == null ? String.valueOf(userId) : userId + ":" + demoSession;
        Deque<Instant> calls = callsByUser.computeIfAbsent(key, id -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                calls.pollFirst();
            }
            long lastMinute = calls.stream().filter(call -> call.isAfter(now.minus(Duration.ofMinutes(1)))).count();
            if (lastMinute >= perMinute || calls.size() >= perHour) {
                log.warn("Assistant limit reached for user {} ({} in the last minute, {} in the last hour)",
                        userId, lastMinute, calls.size());
                return false;
            }
            if (!takeFromDailyBudget(now)) {
                return false;
            }
            calls.addLast(now);
            return true;
        }
    }

    private synchronized boolean takeFromDailyBudget(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, clock.getZone());
        if (!date.equals(today)) {
            today = date;
            callsToday = 0;
        }
        if (callsToday >= perDay) {
            log.warn("Assistant daily limit of {} model calls reached", perDay);
            return false;
        }
        callsToday++;
        return true;
    }
}
