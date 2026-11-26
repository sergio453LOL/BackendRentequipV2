package com.rentequip.backend.security;

import com.rentequip.backend.enums.ReceiptChannel;
import com.rentequip.backend.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T15:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void theEventPastTheLimitWaitsUntilTheOldestLeavesTheWindow() {
        Duration window = Duration.ofMinutes(10);
        limiter.acquire("b", "k", 2, window);
        clock.advance(Duration.ofMinutes(4));
        limiter.acquire("b", "k", 2, window);

        assertThatThrownBy(() -> limiter.acquire("b", "k", 2, window))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds())
                        .isBetween(360L, 361L))
                .extracting("errorCode").isEqualTo("TOO_MANY_REQUESTS");

        // Other keys and other buckets keep their own counters.
        assertThatCode(() -> limiter.acquire("b", "other", 2, window)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire("c", "k", 2, window)).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(6));
        assertThatCode(() -> limiter.acquire("b", "k", 2, window)).doesNotThrowAnyException();
    }

    @Test
    void failuresAreRecordedApartAndASuccessForgetsThem() {
        Duration window = Duration.ofMinutes(15);
        for (int i = 0; i < 3; i++) {
            limiter.check("login", "ana@x.pe", 3, window);
            limiter.record("login", "ana@x.pe", window);
        }
        assertThatThrownBy(() -> limiter.check("login", "ana@x.pe", 3, window))
                .isInstanceOf(TooManyRequestsException.class);

        limiter.reset("login", "ana@x.pe");
        assertThatCode(() -> limiter.check("login", "ana@x.pe", 3, window)).doesNotThrowAnyException();
    }

    @Test
    void receiptsByPhoneHaveACooldownPerChannelAndADailyCapButEmailIsFree() {
        AbuseGuard guard = new AbuseGuard(limiter, 10, 50, 15, 10, 40, 1000, 60, 2);

        guard.beforeReceipt(7L, ReceiptChannel.SMS);
        assertThatThrownBy(() -> guard.beforeReceipt(7L, ReceiptChannel.SMS))
                .isInstanceOf(TooManyRequestsException.class);
        // Another channel is not in cooldown, and email is never limited.
        guard.beforeReceipt(7L, ReceiptChannel.WHATSAPP);
        for (int i = 0; i < 5; i++) {
            guard.beforeReceipt(7L, ReceiptChannel.EMAIL);
        }

        // Two paid sends a day: after the cooldown the third one still waits for tomorrow.
        clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(() -> guard.beforeReceipt(7L, ReceiptChannel.SMS))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> guard.beforeReceipt(8L, ReceiptChannel.SMS)).doesNotThrowAnyException();
    }
}
