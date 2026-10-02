package dev.leilaalgarve.apikey.validation;

import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.activeKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Invalid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Valid;
import dev.leilaalgarve.apikey.validation.IssuedKeyMother.IssuedKey;
import example.consumer.ConsumerTestApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Spring-managed ApiKeyValidator a consumer gets injected: which "now" it uses with and
 * without a Clock bean of the consumer's own (contracts/validation-api.md, "Clock").
 */
class ApiKeyValidatorWiringTest {

    @Nested
    @SpringBootTest(classes = ConsumerTestApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
    @Transactional
    class WithoutAClockBean {

        @Autowired
        private ApiKeyValidator validator;

        @Autowired
        private ApiKeyRepository repository;

        @Autowired
        private ApiKeyHasher hasher;

        @Test
        void fallsBackToTheSystemClock() {
            Instant realNow = Instant.now();
            IssuedKey stillValid = activeKey(realNow)
                    .expiringAt(realNow.plus(1, ChronoUnit.HOURS)).saveWith(repository, hasher);
            IssuedKey alreadyExpired = activeKey(realNow)
                    .expiringAt(realNow.minus(1, ChronoUnit.HOURS)).saveWith(repository, hasher);

            assertThat(validator.validate(stillValid.rawKey())).isEqualTo(new Valid("jogo-acoes"));
            assertThat(validator.validate(alreadyExpired.rawKey()))
                    .isEqualTo(new Invalid(ApiKeyFailureReason.EXPIRED));
        }
    }

    @Nested
    @SpringBootTest(classes = ConsumerTestApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
    @Import(WithTheConsumersClockBean.ConsumerClock.class)
    @Transactional
    class WithTheConsumersClockBean {

        private static final Instant CONSUMER_NOW = Instant.parse("2000-01-01T00:00:00Z");

        @TestConfiguration
        static class ConsumerClock {

            @Bean
            Clock clock() {
                return Clock.fixed(CONSUMER_NOW, ZoneOffset.UTC);
            }
        }

        @Autowired
        private ApiKeyValidator validator;

        @Autowired
        private ApiKeyRepository repository;

        @Autowired
        private ApiKeyHasher hasher;

        @Test
        void usesTheConsumersClockInsteadOfTheSystemOne() {
            // Long expired by the real clock, still valid by the consumer's fixed one.
            IssuedKey issued = activeKey(CONSUMER_NOW)
                    .expiringAt(CONSUMER_NOW.plus(1, ChronoUnit.DAYS)).saveWith(repository, hasher);

            assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("jogo-acoes"));
        }
    }
}
