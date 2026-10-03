package dev.leilaalgarve.apikey;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the actual entry point -- proves the application boots for real through its
 * own main() (not just through Spring's test context loader, which ApiKeysMigrationTest
 * already exercises) AND runs a full "generate" invocation end to end, against the same real
 * PostgreSQL the rest of the suite uses (see src/test/resources/application.yml).
 *
 * main() commits the generated key for real (there's no test transaction to roll back), so
 * the key is deleted afterwards by its per-run client name -- otherwise it would pile up in a
 * local Postgres that persists between runs.
 *
 * Deliberately exercises the success path only (exit code 0): ApiKeyCliRunner only calls
 * ProcessExiter.exit() -- the real System.exit() in production -- for a non-zero code, so a
 * failure path here would kill this test's own JVM. Every failure path is already covered
 * without that risk by GenerateCommandTest/GenerateCommandMissingPepperTest, which call
 * GenerateCommand directly instead of going through main().
 */
class DeployoApiKeyApplicationTests {

    private final String clientName = "smoke-test-client-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void mainBootsTheApplicationAndGeneratesAKey() {
        assertThatCode(() -> DeployoApiKeyApplication.main(new String[] {
                "generate",
                "--client", clientName,
                "--API_KEY_HMAC_PEPPER=smoke-test-pepper"
        })).doesNotThrowAnyException();
    }

    @AfterEach
    void deleteGeneratedKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        env("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/deployo_api_key"),
                        env("SPRING_DATASOURCE_USERNAME", "deployo_api_key_admin"),
                        env("SPRING_DATASOURCE_PASSWORD", "deployo_api_key_admin"));
                PreparedStatement delete = connection.prepareStatement("DELETE FROM api_keys WHERE client_name = ?")) {
            delete.setString(1, clientName);
            delete.executeUpdate();
        }
    }

    // Same defaults as src/test/resources/application.yml -- main() resolves the datasource from
    // there, and this cleanup has no Spring context of its own to ask.
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
