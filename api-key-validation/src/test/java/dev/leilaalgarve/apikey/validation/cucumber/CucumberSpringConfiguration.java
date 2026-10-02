package dev.leilaalgarve.apikey.validation.cucumber;

import example.consumer.ConsumerTestApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * One Spring context for the whole suite, booted like a protected service would boot it
 * (ConsumerTestApplication). The service's Clock bean is fixed at {@link #NOW}, so the injected
 * ApiKeyValidator -- which picks up a consumer's Clock -- and the steps agree on "now" exactly.
 *
 * <p>No per-scenario rollback (cucumber-spring doesn't wrap scenarios in one): every key gets a
 * fresh random plaintext, so scenarios never see each other's rows through a lookup by hash.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = ConsumerTestApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
@Import(CucumberSpringConfiguration.FixedClock.class)
public class CucumberSpringConfiguration {

    static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
