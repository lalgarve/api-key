package dev.leilaalgarve.apikey.validation;

import java.util.Objects;

/**
 * Outcome of {@link ApiKeyValidator#validate(String)} -- a typed result rather than a boolean,
 * so the caller can tell why a key was rejected (spec.md FR2). Exhaustive in a {@code switch}.
 */
public sealed interface ApiKeyValidationResult {

    /** The key is issued, not revoked and not expired; {@code clientName} is the one given to {@code --client}. */
    record Valid(String clientName) implements ApiKeyValidationResult {

        public Valid {
            Objects.requireNonNull(clientName, "clientName");
        }
    }

    /** The key was rejected; see {@link ApiKeyFailureReason} before exposing the reason externally. */
    record Invalid(ApiKeyFailureReason reason) implements ApiKeyValidationResult {

        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
