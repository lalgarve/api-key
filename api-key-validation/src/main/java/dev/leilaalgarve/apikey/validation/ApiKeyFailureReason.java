package dev.leilaalgarve.apikey.validation;

/**
 * Why a presented API key was rejected -- a code only, with no HTTP status or message: mapping
 * each reason to a response (status, wording, language, how much to reveal) is the consuming
 * service's decision (specs/008-validate-api-key/plan.md, "Decisões de arquitetura").
 *
 * <p>Telling these apart to an external caller lets anyone holding a leaked key learn whether
 * it ever existed ({@link #REVOKED}/{@link #EXPIRED}) or not ({@link #NOT_FOUND}). Public APIs
 * usually answer every reason the same way and keep the exact one for their own logs -- see
 * specs/008-validate-api-key/http-integration.md.
 */
public enum ApiKeyFailureReason {

    /** No key was presented: null, empty or blank. */
    MISSING,

    /** Not shaped like a key this project generates; rejected without hashing or querying. */
    MALFORMED,

    /** Well-formed, but its hash matches no issued key. */
    NOT_FOUND,

    /** Issued and not revoked, but past its expiration. */
    EXPIRED,

    /** Issued and revoked; takes precedence over {@link #EXPIRED} when both apply. */
    REVOKED
}
