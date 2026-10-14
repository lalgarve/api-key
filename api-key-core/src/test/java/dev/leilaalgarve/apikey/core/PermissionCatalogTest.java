package dev.leilaalgarve.apikey.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leilaalgarve.apikey.core.PermissionCatalog.Entry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** Every rule of specs/013-key-permissions/spec.md FR2-FR4 and FR13/FR16. */
class PermissionCatalogTest {

    private static final List<Entry> EMAIL_SERVICE = List.of(
            new Entry("email:send", true),
            new Entry("disposable-email:admin", false));

    @Test
    void noEntriesMeansNoCatalog() {
        PermissionCatalog catalog = PermissionCatalog.none();

        assertThat(catalog.isConfigured()).isFalse();
        assertThat(catalog.names()).isEmpty();
        assertThat(catalog.defaults()).isEmpty();
    }

    @Test
    void readsNamesAndDefaultsInAlphabeticalOrder() {
        PermissionCatalog catalog = new PermissionCatalog(EMAIL_SERVICE);

        assertThat(catalog.isConfigured()).isTrue();
        assertThat(catalog.names()).containsExactly("disposable-email:admin", "email:send");
        assertThat(catalog.defaults()).containsExactly("email:send");
        assertThat(catalog.contains("email:send")).isTrue();
        assertThat(catalog.contains("email:delete")).isFalse();
    }

    @Test
    void rejectsANameOutsideTheFormat() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(new Entry("Email:Send", true)));

        assertThatThrownBy(catalog::requireValid)
                .isInstanceOf(InvalidPermissionCatalogException.class)
                .hasMessage("Error: invalid permission name 'Email:Send' in api-key.permissions.");
    }

    @Test
    void rejectsAMissingName() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(new Entry(null, true)));

        assertThatThrownBy(catalog::requireValid)
                .hasMessage("Error: invalid permission name '' in api-key.permissions.");
    }

    @Test
    void acceptsANameOfExactly100CharactersAndRejects101() {
        String hundred = "a".repeat(100);
        assertThat(new PermissionCatalog(List.of(new Entry(hundred, true))).names()).containsExactly(hundred);

        PermissionCatalog tooLong = new PermissionCatalog(List.of(new Entry(hundred + "a", true)));
        assertThatThrownBy(tooLong::isConfigured).isInstanceOf(InvalidPermissionCatalogException.class);
    }

    @Test
    void rejectsARepeatedName() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(
                new Entry("email:send", true), new Entry("email:send", false)));

        assertThatThrownBy(catalog::names)
                .hasMessage("Error: permission 'email:send' is declared more than once in api-key.permissions.");
    }

    @Test
    void rejectsACatalogWithoutDefaults() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(new Entry("email:send", false)));

        assertThatThrownBy(catalog::defaults)
                .hasMessage("Error: api-key.permissions must mark at least one permission as default.");
    }

    @Test
    void everyMethodRefusesAnInvalidCatalog() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(new Entry("email:send", false)));
        ApiKey key = new ApiKey("jogo-acoes", "hash", Instant.now(), null);

        assertThatThrownBy(() -> catalog.contains("email:send")).isInstanceOf(InvalidPermissionCatalogException.class);
        assertThatThrownBy(() -> catalog.effectivePermissions(key)).isInstanceOf(InvalidPermissionCatalogException.class);
    }

    @Test
    void effectivePermissionsAreTheStoredOnes() {
        PermissionCatalog catalog = new PermissionCatalog(EMAIL_SERVICE);
        ApiKey key = new ApiKey("email-admin", "hash", Instant.now(), null, Set.of("disposable-email:admin"));

        assertThat(catalog.effectivePermissions(key)).containsExactly("disposable-email:admin");
    }

    @Test
    void aKeyWithoutStoredPermissionsGetsTheDefaults() {
        PermissionCatalog catalog = new PermissionCatalog(EMAIL_SERVICE);
        ApiKey key = new ApiKey("jogo-acoes", "hash", Instant.now(), null);

        assertThat(catalog.effectivePermissions(key)).containsExactly("email:send");
    }

    @Test
    void withoutACatalogAKeyWithoutStoredPermissionsHasNone() {
        ApiKey key = new ApiKey("jogo-acoes", "hash", Instant.now(), null);

        assertThat(PermissionCatalog.none().effectivePermissions(key)).isEmpty();
    }

    @Test
    void storedPermissionsSurviveLeavingTheCatalog() {
        PermissionCatalog catalog = new PermissionCatalog(EMAIL_SERVICE);
        ApiKey key = new ApiKey("old-client", "hash", Instant.now(), null, Set.of("email:legacy"));

        assertThat(catalog.effectivePermissions(key)).containsExactly("email:legacy");
    }

    @Test
    void bindsFromConfigurationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("api-key.permissions[0].name", "email:send")
                .withProperty("api-key.permissions[0].default", "true")
                .withProperty("api-key.permissions[1].name", "disposable-email:admin");

        PermissionCatalog catalog = new PermissionCatalog(environment);

        assertThat(catalog.names()).containsExactly("disposable-email:admin", "email:send");
        assertThat(catalog.defaults()).containsExactly("email:send");
    }

    @Test
    void bindsFromEnvironmentVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("API_KEY_PERMISSIONS_0_NAME", "email:send",
                        "API_KEY_PERMISSIONS_0_DEFAULT", "true")));

        PermissionCatalog catalog = new PermissionCatalog(environment);

        assertThat(catalog.defaults()).containsExactly("email:send");
    }

    @Test
    void absentPropertyMeansNoCatalog() {
        assertThat(new PermissionCatalog(new MockEnvironment()).isConfigured()).isFalse();
    }

    @Test
    void anUnreadableDefaultIsAConfigurationProblem() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("api-key.permissions[0].name", "email:send")
                .withProperty("api-key.permissions[0].default", "maybe");

        PermissionCatalog catalog = new PermissionCatalog(environment);

        assertThatThrownBy(catalog::requireValid)
                .hasMessage("Error: api-key.permissions is not a valid list of {name, default} entries.");
    }
}
