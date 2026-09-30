package io.deployo.apikey;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Smoke test for the actual entry point -- proves the application boots for real through its
 * own main() (not just through Spring's test context loader, which ApiKeysMigrationTest
 * already exercises) AND runs a full "generate" invocation end to end. Uses its own isolated
 * in-memory H2 database so it doesn't interfere with the schema state other tests depend on.
 *
 * Deliberately exercises the success path only (exit code 0): ApiKeyCliRunner only calls
 * ProcessExiter.exit() -- the real System.exit() in production -- for a non-zero code, so a
 * failure path here would kill this test's own JVM. Every failure path is already covered
 * without that risk by GenerateCommandTest/GenerateCommandMissingPepperTest, which call
 * GenerateCommand directly instead of going through main().
 *
 * Overrides both the datasource URL AND driver-class-name: when CI runs with
 * SPRING_PROFILES_ACTIVE=docker, application-docker.yml sets driver-class-name to
 * org.postgresql.Driver, which otherwise wins over this test's H2 URL and fails with
 * "Driver org.postgresql.Driver claims to not accept jdbcUrl, jdbc:h2:...". Invisible locally
 * without Docker, since the docker profile was never actually exercised there.
 */
class DeployoApiKeyApplicationTests {

    @Test
    void mainBootsTheApplicationAndGeneratesAKey() {
        assertThatCode(() -> DeployoApiKeyApplication.main(new String[] {
                "generate",
                "--client", "smoke-test-client",
                "--spring.datasource.url=jdbc:h2:mem:deployo_api_key_boot;MODE=PostgreSQL",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--API_KEY_HMAC_PEPPER=smoke-test-pepper"
        })).doesNotThrowAnyException();
    }
}
