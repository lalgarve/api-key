package dev.leilaalgarve.apikey.core;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The permissions this instance can grant, read from `api-key.permissions`
 * (specs/013-key-permissions/contracts/cli-commands.md, "Catálogo de permissões"). Shared by
 * the CLI and the validation library, so both resolve a key's permissions the same way.
 *
 * <p>An invalid configuration never stops the Spring context from starting: the problem is
 * kept and every method throws {@link InvalidPermissionCatalogException} -- see
 * {@link #requireValid()}.
 */
@Component
public final class PermissionCatalog {

    public static final String PROPERTY = "api-key.permissions";

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z0-9-]+)*$");
    private static final int MAX_NAME_LENGTH = 100;

    /** One configured permission. */
    public record Entry(String name, boolean isDefault) {
    }

    private final SortedSet<String> names = new TreeSet<>();
    private final SortedSet<String> defaults = new TreeSet<>();
    private final String problem;

    @Autowired
    public PermissionCatalog(Environment environment) {
        String bindProblem = null;
        List<Entry> entries = List.of();
        try {
            entries = Binder.get(environment)
                    .bind(PROPERTY, Bindable.listOf(Declaration.class))
                    .map(list -> list.stream().map(Declaration::toEntry).toList())
                    .orElse(List.of());
        } catch (BindException e) {
            bindProblem = "Error: " + PROPERTY + " is not a valid list of {name, default} entries.";
        }
        this.problem = bindProblem != null ? bindProblem : load(entries);
    }

    public PermissionCatalog(List<Entry> entries) {
        this.problem = load(entries);
    }

    /** No catalog: every command and the validator behave as before 1.1.0. */
    public static PermissionCatalog none() {
        return new PermissionCatalog(List.of());
    }

    private String load(List<Entry> entries) {
        for (Entry entry : entries) {
            String name = entry.name() == null ? "" : entry.name();
            if (name.length() > MAX_NAME_LENGTH || !NAME_PATTERN.matcher(name).matches()) {
                return "Error: invalid permission name '" + name + "' in " + PROPERTY + ".";
            }
            if (!names.add(name)) {
                return "Error: permission '" + name + "' is declared more than once in " + PROPERTY + ".";
            }
            if (entry.isDefault()) {
                defaults.add(name);
            }
        }
        if (!names.isEmpty() && defaults.isEmpty()) {
            return "Error: " + PROPERTY + " must mark at least one permission as default.";
        }
        return null;
    }

    /** @throws InvalidPermissionCatalogException with the CLI's message when the configuration is invalid */
    public void requireValid() {
        if (problem != null) {
            throw new InvalidPermissionCatalogException(problem);
        }
    }

    /** False when `api-key.permissions` is absent or empty. */
    public boolean isConfigured() {
        requireValid();
        return !names.isEmpty();
    }

    public SortedSet<String> names() {
        requireValid();
        return Collections.unmodifiableSortedSet(names);
    }

    public SortedSet<String> defaults() {
        requireValid();
        return Collections.unmodifiableSortedSet(defaults);
    }

    public boolean contains(String name) {
        requireValid();
        return names.contains(name);
    }

    /**
     * The key's stored permissions; a key with none (issued before 1.1.0, or on an instance
     * without a catalog) gets the catalog's defaults -- empty when there is no catalog
     * (specs/013-key-permissions/spec.md FR13). Stored permissions are kept even if they left
     * the catalog since (FR16).
     */
    public SortedSet<String> effectivePermissions(ApiKey key) {
        requireValid();
        SortedSet<String> stored = key.getPermissions();
        return stored.isEmpty() ? Collections.unmodifiableSortedSet(defaults) : stored;
    }

    /** Binding target for one `api-key.permissions[n]` entry; `default` is a Java keyword, hence the bean. */
    static class Declaration {

        private String name;
        private boolean isDefault;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean getDefault() {
            return isDefault;
        }

        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }

        Entry toEntry() {
            return new Entry(name, isDefault);
        }
    }
}
