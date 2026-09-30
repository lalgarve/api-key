package io.deployo.apikey.issuance;

/**
 * Seam around {@link System#exit(int)} so the CLI's exit-code path can be unit-tested without
 * ever terminating the JVM running the test.
 */
@FunctionalInterface
public interface ProcessExiter {

    void exit(int code);
}
