package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window counters: "at most N events per key in the last W". The backend runs as a single
 * instance, so memory is enough; a restart simply forgets the counters.
 *
 * <p>Each limit has its own bucket name, so a login failure and an SMS send never share a counter. Keys are
 * whatever identifies the caller for that limit: an IP, an email, a user id.</p>
 */
@Component
public class RateLimiter {

    /** Past this many keys the expired ones are swept, so a flood of distinct keys cannot grow memory forever. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records one event and throws 429 when the key already used up its limit; the event is then not counted. */
    public void acquire(String bucket, String key, int max, Duration window) {
        long wait = tryAcquire(bucket, key, max, window);
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }
    }

    /** Throws 429 when the key is over its limit, without recording anything (for "count only failures"). */
    public void check(String bucket, String key, int max, Duration window) {
        long wait = secondsUntilFree(bucket, key, max, window);
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }
    }

    /** Records one event without checking: a failed login, for instance, counted after it happened. */
    public void record(String bucket, String key, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> deque = events.computeIfAbsent(id(bucket, key), ignored -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now, window);
            deque.addLast(now);
        }
        sweepIfLarge(window);
    }

    /** Forgets the key: a successful login clears that email's failures. */
    public void reset(String bucket, String key) {
        events.remove(id(bucket, key));
    }

    /** 0 when the event was recorded, otherwise the seconds to wait before the oldest one leaves the window. */
    long tryAcquire(String bucket, String key, int max, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> deque = events.computeIfAbsent(id(bucket, key), ignored -> new ArrayDeque<>());
        long wait;
        synchronized (deque) {
            prune(deque, now, window);
            if (deque.size() >= max) {
                wait = secondsLeft(deque.peekFirst(), now, window);
            } else {
                deque.addLast(now);
                wait = 0;
            }
        }
        sweepIfLarge(window);
        return wait;
    }

    private long secondsUntilFree(String bucket, String key, int max, Duration window) {
        Deque<Instant> deque = events.get(id(bucket, key));
        if (deque == null) {
            return 0;
        }
        Instant now = clock.instant();
        synchronized (deque) {
            prune(deque, now, window);
            return deque.size() >= max ? secondsLeft(deque.peekFirst(), now, window) : 0;
        }
    }

    private static long secondsLeft(Instant oldest, Instant now, Duration window) {
        return Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds() + 1);
    }

    private static void prune(Deque<Instant> deque, Instant now, Duration window) {
        Instant limit = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.pollFirst();
        }
    }

    private void sweepIfLarge(Duration window) {
        if (events.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant now = clock.instant();
        events.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now, window);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String id(String bucket, String key) {
        return bucket + '|' + (key == null ? "" : key);
    }
}
