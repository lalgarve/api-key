package dev.leilaalgarve.apikey.management;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.apikey.core.PermissionCatalog;
import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** The PERMISSIONS column of specs/013-key-permissions/contracts/cli-commands.md. */
@SpringBootTest(classes = DeployoApiKeyApplication.class)
@Transactional
class ListCommandPermissionsTest {

    private static final PermissionCatalog EMAIL_SERVICE = new PermissionCatalog(List.of(
            new Entry("email:send", true), new Entry("disposable-email:admin", false)));

    @Autowired
    private ApiKeyRepository repository;

    @Test
    void withACatalogTheLastColumnShowsTheEffectivePermissions() {
        ApiKey admin = repository.save(new ApiKey("list-perm-1", "hash-list-perm-admin", Instant.now(), null,
                Set.of("email:send", "disposable-email:admin")));
        ApiKey legacy = repository.save(new ApiKey("list-perm-1", "hash-list-perm-legacy", Instant.now(), null));

        String[] lines = run(EMAIL_SERVICE, "list-perm-1");

        assertThat(lines[0]).endsWith("STATUS  PERMISSIONS");
        assertThat(rowOf(lines, admin)).endsWith("active  disposable-email:admin,email:send");
        assertThat(rowOf(lines, legacy)).endsWith("email:send");
    }

    @Test
    void withoutACatalogThereIsNoPermissionsColumn() {
        repository.save(new ApiKey("list-perm-2", "hash-list-perm-none", Instant.now(), null, Set.of("email:send")));

        String[] lines = run(PermissionCatalog.none(), "list-perm-2");

        assertThat(lines[0]).endsWith("STATUS").doesNotContain("PERMISSIONS");
        assertThat(lines[1]).endsWith("active");
    }

    private String[] run(PermissionCatalog catalog, String client) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        int exitCode = new ListCommand(repository, catalog).execute(new String[] {"list", "--client", client},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertThat(exitCode).isZero();
        return outBytes.toString(StandardCharsets.UTF_8).split("\n");
    }

    private static String rowOf(String[] lines, ApiKey key) {
        return Arrays.stream(lines).skip(1)
                .filter(line -> line.startsWith(key.getId() + " "))
                .findFirst().orElseThrow();
    }
}
