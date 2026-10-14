package dev.leilaalgarve.apikey.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * `generate --permissions` per specs/013-key-permissions/contracts/cli-commands.md, against the
 * real generator, hasher and database. The catalog is built per test (the context has none),
 * the same way the Cucumber steps do.
 */
@SpringBootTest(classes = DeployoApiKeyApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
@Transactional
class GenerateCommandPermissionsTest {

    private static final PermissionCatalog EMAIL_SERVICE = new PermissionCatalog(List.of(
            new Entry("email:send", true), new Entry("disposable-email:admin", false)));

    @Autowired
    private ApiKeyGenerator generator;

    @Autowired
    private ApiKeyHasher hasher;

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void withoutTheOptionTheKeyGetsTheDefaults() {
        Result result = run(EMAIL_SERVICE, "generate", "--client", "perm-defaults");

        assertThat(result.exitCode).isZero();
        assertThat(lastLine(result.out)).isEqualTo("Permissions: email:send.");
        assertThat(onlyKeyOf("perm-defaults").getPermissions()).containsExactly("email:send");
    }

    @Test
    void withTheOptionTheKeyGetsOnlyTheListedOnes() {
        Result result = run(EMAIL_SERVICE, "generate", "--client", "perm-listed", "--permissions", "disposable-email:admin");

        assertThat(result.exitCode).isZero();
        assertThat(lastLine(result.out)).isEqualTo("Permissions: disposable-email:admin.");
        assertThat(onlyKeyOf("perm-listed").getPermissions()).containsExactly("disposable-email:admin");
    }

    @Test
    void severalPermissionsAreSortedAndRepeatsAndSpacesIgnored() {
        Result result = run(EMAIL_SERVICE,
                "generate", "--client", "perm-several", "--permissions", " email:send , disposable-email:admin,email:send");

        assertThat(result.exitCode).isZero();
        assertThat(lastLine(result.out)).isEqualTo("Permissions: disposable-email:admin, email:send.");
        assertThat(onlyKeyOf("perm-several").getPermissions()).containsExactly("disposable-email:admin", "email:send");
    }

    @Test
    void anUnknownPermissionFailsWithoutSavingOrPrinting() {
        Result result = run(EMAIL_SERVICE,
                "generate", "--client", "perm-unknown", "--permissions", "email:send,email:delete,email:purge");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo(
                "Error: unknown permission 'email:delete'. Known permissions: disposable-email:admin, email:send.\n");
        assertThat(result.out).isEmpty();
        assertThat(repository.findByClientName("perm-unknown")).isEmpty();
    }

    @Test
    void anEmptyListFailsWithoutSavingOrPrinting() {
        Result result = run(EMAIL_SERVICE, "generate", "--client", "perm-empty", "--permissions", " , ");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo("Error: --permissions must list at least one permission.\n");
        assertThat(result.out).isEmpty();
        assertThat(repository.findByClientName("perm-empty")).isEmpty();
    }

    @Test
    void theOptionNeedsACatalog() {
        Result result = run(PermissionCatalog.none(), "generate", "--client", "perm-no-catalog", "--permissions", "email:send");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo(
                "Error: --permissions requires a permission catalog. None is configured for this instance.\n");
        assertThat(result.out).isEmpty();
        assertThat(repository.findByClientName("perm-no-catalog")).isEmpty();
    }

    @Test
    void withoutACatalogTheOutputHasNoPermissionsLine() {
        Result result = run(PermissionCatalog.none(), "generate", "--client", "perm-none");

        assertThat(result.exitCode).isZero();
        assertThat(result.out).doesNotContain("Permissions:");
        assertThat(onlyKeyOf("perm-none").getPermissions()).isEmpty();
    }

    @Test
    void thePermissionsLineComesAfterTheRotationLine() {
        repository.save(new ApiKey("perm-rotation", "hash-perm-rotation-old", Instant.now(), null));

        Result result = run(EMAIL_SERVICE, "generate", "--client", "perm-rotation", "--revoke-old-in-days", "7");

        assertThat(result.exitCode).isZero();
        String[] lines = result.out.split("\n");
        assertThat(lines[lines.length - 2]).startsWith("1 existing key for client 'perm-rotation' scheduled for revocation");
        assertThat(lines[lines.length - 1]).isEqualTo("Permissions: email:send.");
    }

    private Result run(PermissionCatalog catalog, String... args) {
        GenerateCommand command = new GenerateCommand(
                generator, hasher, repository, new OldKeyRotationPolicy(repository), catalog);
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode = command.execute(args,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        return new Result(exitCode, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
    }

    private ApiKey onlyKeyOf(String client) {
        List<ApiKey> keys = repository.findByClientName(client);
        assertThat(keys).hasSize(1);
        return keys.get(0);
    }

    private static String lastLine(String out) {
        String[] lines = out.split("\n");
        return lines[lines.length - 1];
    }

    private record Result(int exitCode, String out, String err) {
    }
}
