# Contrato de API: key-permissions

Escrito antes da implementação — a implementação segue o contrato. Documenta só a
**diferença** em relação a
[`008-validate-api-key/contracts/validation-api.md`](../../008-validate-api-key/contracts/validation-api.md).
`ApiKeyFailureReason` e a tabela de `validate` não mudam.

## `ApiKeyValidationResult.Valid` (alterado)

```java
public sealed interface ApiKeyValidationResult {

    record Valid(String clientName, Set<String> permissions) implements ApiKeyValidationResult {

        public Valid {
            permissions = Set.copyOf(permissions);
        }

        /** Same as 1.0.x: a valid key with no permissions. */
        public Valid(String clientName) {
            this(clientName, Set.of());
        }

        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }
    }

    record Invalid(ApiKeyFailureReason reason) implements ApiKeyValidationResult {}
}
```

- `permissions()` nunca é nulo e é imutável. Vazio quando a instância não tem catálogo e a
  chave não tem permissão gravada.
- `hasPermission` compara o nome exato, sem curinga nem hierarquia.
- O construtor de um argumento existe para não quebrar quem já cria um `Valid` (ex.: em teste
  do serviço protegido).

**Compatibilidade:** `case Valid valid -> valid.clientName()` (a forma do README) continua
compilando. Desconstruir com padrão de record (`case Valid(String clientName)`) deixa de
compilar, porque o record ganhou um componente. Precisa constar na nota de release da 1.1.0.

**Uso no serviço protegido:**

```java
switch (validator.validate(request.getHeader("X-API-Key"))) {
    case ApiKeyValidationResult.Valid valid when valid.hasPermission("disposable-email:admin") -> {
        // pode administrar e-mails descartáveis
    }
    case ApiKeyValidationResult.Valid valid -> {
        // autenticado, mas sem a permissão: o serviço decide (ex.: HTTP 403)
    }
    case ApiKeyValidationResult.Invalid invalid -> {
        // inalterado
    }
}
```

## `ApiKeyValidator` (alterado)

`validate` não muda de assinatura. Como o validador agora lê o catálogo (FR13 de `spec.md`),
ganha um construtor; os que já existem continuam existindo:

```java
public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher, Clock clock) { ... }
// igual à 1.0.x: sem catálogo

public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher, Clock clock,
        PermissionCatalog catalog) { ... }   // novo

@Autowired
public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher,
        ObjectProvider<Clock> clock, PermissionCatalog catalog) { ... }
```

O construtor do Spring troca de assinatura (ganha o catálogo), mas quem consome a biblioteca
recebe o validador por injeção e não chama esse construtor. O `PermissionCatalog` é registrado
pela própria biblioteca e fica sem catálogo quando `api-key.permissions` não está configurado.
Quem cria o validador à mão (ex.: em teste) com o construtor de três argumentos continua com o
comportamento da 1.0.x.

O que muda é o conteúdo de `Valid`:

| Chave encontrada, válida | `Valid.permissions()` |
|---|---|
| Com permissões gravadas | As permissões gravadas, mesmo que alguma tenha saído do catálogo |
| Sem permissões gravadas, instância com catálogo | As permissões padrão do catálogo (FR13 de `spec.md`) |
| Sem permissões gravadas, instância sem catálogo | Vazio |

As permissões da chave encontrada vêm junto com ela (`ApiKey.getPermissions()`), sem uma
consulta extra por chave em `list` (ver `plan.md`).

Se o catálogo estiver inválido, `validate` lança `InvalidPermissionCatalogException` em toda
chamada que passa do ramo `MALFORMED`, do mesmo jeito que já lança `MissingHmacPepperException`
sem pepper. É falha de configuração, não um resultado do domínio.

## `ApiKey` (alterado, `api-key-core`)

Ganha as permissões gravadas da chave:

```java
public Set<String> getPermissions();   // imutável, nunca nulo; vazio para chaves anteriores à 1.1.0
```

A criação de uma chave passa a receber as permissões. Os construtores e fábricas existentes
continuam existindo e criam a chave sem permissões.

## `PermissionCatalog` (novo, `api-key-core`)

Leitura de `api-key.permissions`, compartilhada pela CLI e pela biblioteca:

```java
package dev.leilaalgarve.apikey.core;

public final class PermissionCatalog {
    public boolean isConfigured();          // false = sem catálogo (comportamento 1.0.x)
    public SortedSet<String> names();       // todas, em ordem alfabética
    public SortedSet<String> defaults();    // as marcadas como padrão
    public boolean contains(String name);

    /** As gravadas na chave; se ela não tiver nenhuma, as padrão (FR13); sem catálogo, vazio. */
    public SortedSet<String> effectivePermissions(ApiKey key);

    /** Lança InvalidPermissionCatalogException se o catálogo estiver inválido. */
    public void requireValid();
}

public class InvalidPermissionCatalogException extends RuntimeException { ... }
```

Um catálogo inválido (nome fora do formato, nome repetido, nenhuma permissão padrão, lista
que não dá para ler) **não**
impede o contexto do Spring de subir. O problema fica guardado, e os métodos acima lançam
`InvalidPermissionCatalogException` com a mensagem do contrato de CLI. A CLI chama
`requireValid()` antes de despachar qualquer comando e traduz a exceção para o exit 2. É o
mesmo padrão do pepper (`ApiKeyHasher` só reclama quando é usado); ver `plan.md`.
