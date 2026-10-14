package dev.leilaalgarve.apikey.core;

/**
 * The `api-key.permissions` configuration is invalid. Thrown when the catalog is used, never
 * while the Spring context starts -- same pattern as {@link MissingHmacPepperException}, so the
 * CLI can still answer with its own exit code and message (specs/013-key-permissions/plan.md).
 * The message is the exact text the CLI prints.
 */
public class InvalidPermissionCatalogException extends RuntimeException {

    public InvalidPermissionCatalogException(String message) {
        super(message);
    }
}
