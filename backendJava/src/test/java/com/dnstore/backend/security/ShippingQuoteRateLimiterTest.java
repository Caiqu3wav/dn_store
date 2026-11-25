package com.dnstore.backend.security;

import com.dnstore.backend.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingQuoteRateLimiterTest {

    private MutableClock clock;
    private ShippingQuoteRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new ShippingQuoteRateLimiter(3, Duration.ofMinutes(1), clock);
    }

    @Test
    void allowsRequestsUpToTheLimitInsideTheWindow() {
        UUID user = UUID.randomUUID();

        assertThatCode(() -> {
            limiter.acquire(user);
            limiter.acquire(user);
            limiter.acquire(user);
        }).doesNotThrowAnyException();
    }

    @Test
    void blocksTheRequestAfterTheLimitWithRetryAfterUntilTheWindowEnds() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.acquire(user);
        }
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.acquire(user))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    void allowsAgainWhenTheWindowHasPassed() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.acquire(user);
        }
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.acquire(user)).doesNotThrowAnyException();
    }

    @Test
    void doesNotLetOneCustomerBlockAnother() {
        UUID abusive = UUID.randomUUID();
        UUID legitimate = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.acquire(abusive);
        }

        assertThatThrownBy(() -> limiter.acquire(abusive)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.acquire(legitimate)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ShippingQuoteRateLimiter(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-18T12:00:00Z");

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
}
