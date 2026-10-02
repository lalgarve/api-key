package dev.leilaalgarve.apikey.validation;

import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.activeKey;
import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.wellFormedRawKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import example.consumer.ConsumerTestApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * spec.md FR6: the plaintext key never reaches a log line, in any branch -- checked with the
 * most verbose logging this path can produce (this library at TRACE, Hibernate's SQL and bound
 * parameters), so a key leaking through a query parameter or a debug line would show up.
 */
@SpringBootTest(classes = ConsumerTestApplication.class, properties = {
        "API_KEY_HMAC_PEPPER=test-pepper",
        "logging.level.dev.leilaalgarve.apikey=TRACE",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE"})
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class ApiKeyValidatorLoggingTest {

    @Autowired
    private ApiKeyValidator validator;

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyHasher hasher;

    @Test
    void plaintextKeyNeverAppearsInTheLogsInAnyBranch(CapturedOutput output) {
        Instant now = Instant.now();
        String neverIssued = wellFormedRawKey();
        List<String> presentedKeys = List.of(
                "dak_tooShort",
                neverIssued,
                activeKey(now).saveWith(repository, hasher).rawKey(),
                activeKey(now).revokedAt(now.minus(1, ChronoUnit.DAYS)).saveWith(repository, hasher).rawKey(),
                activeKey(now).expiringAt(now.minus(1, ChronoUnit.DAYS)).saveWith(repository, hasher).rawKey());

        presentedKeys.forEach(validator::validate);

        // Sanity check that query parameters really were logged -- otherwise "not in the logs"
        // proves nothing. This hash is only ever bound by the lookup, never by an insert.
        assertThat(output.getAll()).contains(hasher.hash(neverIssued));
        for (String rawKey : presentedKeys) {
            assertThat(output.getAll()).doesNotContain(rawKey);
        }
    }
}
