package dev.leilaalgarve.apikey.validation;

import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.activeKey;
import static dev.leilaalgarve.apikey.validation.IssuedKeyMother.wellFormedRawKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.InvalidPermissionCatalogException;
import dev.leilaalgarve.apikey.core.MissingHmacPepperException;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Invalid;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult.Valid;
import dev.leilaalgarve.apikey.validation.IssuedKeyMother.IssuedKey;
import example.consumer.ConsumerTestApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every row of contracts/validation-api.md's validate() table, against the real repository and
 * hasher on the test database (memory/constitution.md, "preferir real a fake"). The clock is
 * fixed so expiry/revocation edges are exact, not "about now".
 */
@SpringBootTest(classes = ConsumerTestApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
@Transactional
class ApiKeyValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ApiKeyHasher hasher;

    private ApiKeyValidator validator;

    @BeforeEach
    void fixClock() {
        validator = new ApiKeyValidator(repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void absentOrBlankKeyIsMissing(String rawKey) {
        assertThat(validator.validate(rawKey)).isEqualTo(new Invalid(ApiKeyFailureReason.MISSING));
    }

    @ParameterizedTest
    @MethodSource("malformedKeys")
    void keyNotInTheGeneratedShapeIsMalformed(String rawKey) {
        assertThat(validator.validate(rawKey)).isEqualTo(new Invalid(ApiKeyFailureReason.MALFORMED));
    }

    static Stream<String> malformedKeys() {
        return Stream.of(
                "not-a-key",
                "dak_",
                "dak_tooShort",
                "sk_" + "A".repeat(43),
                "dak_" + "A".repeat(42),
                "dak_" + "A".repeat(44),
                "dak_" + "A".repeat(42) + "+");
    }

    @Test
    void wellFormedKeyWithSurroundingWhitespaceIsMalformed() {
        IssuedKey issued = activeKey(NOW).saveWith(repository, hasher);

        assertThat(validator.validate(" " + issued.rawKey() + " "))
                .isEqualTo(new Invalid(ApiKeyFailureReason.MALFORMED));
    }

    @Test
    void wellFormedKeyThatWasNeverIssuedIsNotFound() {
        activeKey(NOW).saveWith(repository, hasher);

        assertThat(validator.validate(wellFormedRawKey()))
                .isEqualTo(new Invalid(ApiKeyFailureReason.NOT_FOUND));
    }

    @Test
    void keyHashedWithAnotherPepperIsNotFound() {
        IssuedKey issued = activeKey(NOW).saveWith(repository, hasher);
        ApiKeyValidator otherPepper = new ApiKeyValidator(
                repository, new ApiKeyHasher("other-pepper"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(otherPepper.validate(issued.rawKey()))
                .isEqualTo(new Invalid(ApiKeyFailureReason.NOT_FOUND));
    }

    @Test
    void activeKeyIsValidForItsClient() {
        IssuedKey issued = activeKey(NOW).forClient("billing").saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("billing"));
    }

    @Test
    void keyWithoutExpirationIsValid() {
        IssuedKey issued = activeKey(NOW).neverExpiring().saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("jogo-acoes"));
    }

    @Test
    void keyRevokedInThePastIsRevoked() {
        IssuedKey issued = activeKey(NOW).revokedAt(NOW.minus(1, ChronoUnit.DAYS)).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.REVOKED));
    }

    @Test
    void keyRevokedExactlyNowIsRevoked() {
        IssuedKey issued = activeKey(NOW).revokedAt(NOW).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.REVOKED));
    }

    @Test
    void keyWithARevocationScheduledLaterIsStillValid() {
        IssuedKey issued = activeKey(NOW).revokedAt(NOW.plusSeconds(1)).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("jogo-acoes"));
    }

    @Test
    void keyPastItsExpirationIsExpired() {
        IssuedKey issued = activeKey(NOW).expiringAt(NOW.minus(1, ChronoUnit.DAYS)).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.EXPIRED));
    }

    @Test
    void keyExpiringExactlyNowIsExpired() {
        IssuedKey issued = activeKey(NOW).expiringAt(NOW).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.EXPIRED));
    }

    @Test
    void keyExpiringOneSecondFromNowIsStillValid() {
        IssuedKey issued = activeKey(NOW).expiringAt(NOW.plusSeconds(1)).saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("jogo-acoes"));
    }

    @Test
    void keyBothExpiredAndRevokedIsReportedAsRevoked() {
        IssuedKey issued = activeKey(NOW)
                .expiringAt(NOW.minus(10, ChronoUnit.DAYS))
                .revokedAt(NOW.minus(1, ChronoUnit.DAYS))
                .saveWith(repository, hasher);

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.REVOKED));
    }

    @Test
    void sameKeyChangesOutcomeAsTheClockMovesPastItsExpiration() {
        IssuedKey issued = activeKey(NOW).expiringAt(NOW.plus(1, ChronoUnit.HOURS)).saveWith(repository, hasher);
        ApiKeyValidator later = new ApiKeyValidator(
                repository, hasher, Clock.fixed(NOW.plus(2, ChronoUnit.HOURS), ZoneOffset.UTC));

        assertThat(validator.validate(issued.rawKey())).isEqualTo(new Valid("jogo-acoes"));
        assertThat(later.validate(issued.rawKey())).isEqualTo(new Invalid(ApiKeyFailureReason.EXPIRED));
    }

    @Test
    void missingPepperIsAnInfrastructureFailureNotAResult() {
        ApiKeyValidator noPepper = new ApiKeyValidator(
                repository, new ApiKeyHasher(""), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> noPepper.validate(wellFormedRawKey()))
                .isInstanceOf(MissingHmacPepperException.class);
    }

    @Test
    void missingAndMalformedAreDecidedWithoutHashingSoTheyDontNeedThePepper() {
        ApiKeyValidator noPepper = new ApiKeyValidator(
                repository, new ApiKeyHasher(""), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(noPepper.validate(null)).isEqualTo(new Invalid(ApiKeyFailureReason.MISSING));
        assertThat(noPepper.validate("not-a-key")).isEqualTo(new Invalid(ApiKeyFailureReason.MALFORMED));
    }

    private ApiKeyValidator withCatalog(PermissionCatalog catalog) {
        return new ApiKeyValidator(repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC), catalog);
    }

    private static final PermissionCatalog EMAIL_SERVICE = new PermissionCatalog(List.of(
            new Entry("email:send", true), new Entry("disposable-email:admin", false)));

    @Test
    void validKeyCarriesItsStoredPermissions() {
        IssuedKey issued = activeKey(NOW).forClient("email-admin")
                .withPermissions("disposable-email:admin").saveWith(repository, hasher);

        assertThat(withCatalog(EMAIL_SERVICE).validate(issued.rawKey()))
                .isEqualTo(new Valid("email-admin", Set.of("disposable-email:admin")));
    }

    @Test
    void keyWithoutStoredPermissionsGetsTheCatalogsDefaults() {
        IssuedKey issued = activeKey(NOW).saveWith(repository, hasher);

        assertThat(withCatalog(EMAIL_SERVICE).validate(issued.rawKey()))
                .isEqualTo(new Valid("jogo-acoes", Set.of("email:send")));
    }

    @Test
    void withoutACatalogAKeyWithoutStoredPermissionsHasNone() {
        IssuedKey issued = activeKey(NOW).saveWith(repository, hasher);

        assertThat(withCatalog(PermissionCatalog.none()).validate(issued.rawKey()))
                .isEqualTo(new Valid("jogo-acoes", Set.of()));
    }

    @Test
    void storedPermissionsAreReturnedEvenIfTheyLeftTheCatalog() {
        IssuedKey issued = activeKey(NOW).withPermissions("email:legacy").saveWith(repository, hasher);

        assertThat(withCatalog(EMAIL_SERVICE).validate(issued.rawKey()))
                .isEqualTo(new Valid("jogo-acoes", Set.of("email:legacy")));
    }

    @Test
    void hasPermissionMatchesTheExactName() {
        IssuedKey issued = activeKey(NOW).withPermissions("disposable-email:admin").saveWith(repository, hasher);

        Valid valid = (Valid) withCatalog(EMAIL_SERVICE).validate(issued.rawKey());

        assertThat(valid.hasPermission("disposable-email:admin")).isTrue();
        assertThat(valid.hasPermission("email:send")).isFalse();
        assertThat(valid.hasPermission("disposable-email")).isFalse();
    }

    @Test
    void anInvalidCatalogIsAnInfrastructureFailureNotAResult() {
        IssuedKey issued = activeKey(NOW).saveWith(repository, hasher);
        ApiKeyValidator misconfigured = withCatalog(new PermissionCatalog(List.of(new Entry("email:send", false))));

        assertThatThrownBy(() -> misconfigured.validate(issued.rawKey()))
                .isInstanceOf(InvalidPermissionCatalogException.class);
        assertThat(misconfigured.validate(null)).isEqualTo(new Invalid(ApiKeyFailureReason.MISSING));
        assertThat(misconfigured.validate("not-a-key")).isEqualTo(new Invalid(ApiKeyFailureReason.MALFORMED));
    }

    @Test
    void validCopiesAndFreezesThePermissions() {
        Set<String> mutable = new java.util.HashSet<>(Set.of("email:send"));
        Valid valid = new Valid("jogo-acoes", mutable);
        mutable.add("disposable-email:admin");

        assertThat(valid.permissions()).containsExactly("email:send");
        assertThatThrownBy(() -> valid.permissions().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new Valid("jogo-acoes").permissions()).isEmpty();
    }
}
