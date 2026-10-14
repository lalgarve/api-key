package dev.leilaalgarve.apikey.validation;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Outcome of {@link ApiKeyValidator#validate(String)} -- a typed result rather than a boolean,
 * so the caller can tell why a key was rejected (spec.md FR2). Exhaustive in a {@code switch}.
 */
public sealed interface ApiKeyValidationResult {

    /**
     * The key is issued, not revoked and not expired; {@code clientName} is the one given to
     * {@code --client}. {@code permissions} are what the key may do in the protected service --
     * deciding whether a call is allowed is the service's job
     * (specs/013-key-permissions/contracts/validation-api.md). Empty on an instance without a
     * permission catalog.
     */
    record Valid(String clientName, Set<String> permissions) implements ApiKeyValidationResult {

        public Valid {
            Objects.requireNonNull(clientName, "clientName");
            permissions = Collections.unmodifiableSortedSet(new TreeSet<>(permissions));
        }

        /** Same as 1.0.x: a valid key with no permissions. */
        public Valid(String clientName) {
            this(clientName, Set.of());
        }

        /** Exact name match -- no wildcard, no hierarchy. */
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }
    }

    /** The key was rejected; see {@link ApiKeyFailureReason} before exposing the reason externally. */
    record Invalid(ApiKeyFailureReason reason) implements ApiKeyValidationResult {

        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
