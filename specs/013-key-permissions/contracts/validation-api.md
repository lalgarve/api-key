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

        /** Same as 1.0/1.1: a valid key with no permissions. */
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
compilar, porque o record ganhou um componente. Precisa constar na nota de release da 1.2.0.

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

Assinaturas públicas e construtores não mudam. O que muda é o conteúdo de `Valid`:

| Chave encontrada, válida | `Valid.permissions()` |
|---|---|
| Com permissões gravadas | As permissões gravadas, mesmo que alguma tenha saído do catálogo |
| Sem permissões gravadas, instância com catálogo | As permissões padrão do catálogo (proposta A de "Decisões em aberto", item 1) |
| Sem permissões gravadas, instância sem catálogo | Vazio |

As permissões são lidas na mesma consulta (ou na mesma transação) que busca a chave pelo hash.

## `ApiKey` (alterado, `api-key-core`)

Ganha as permissões gravadas da chave:

```java
public Set<String> getPermissions();   // imutável, nunca nulo; vazio para chaves anteriores à 1.2.0
```

A criação de uma chave passa a receber as permissões. Os construtores e fábricas existentes
continuam existindo e criam a chave sem permissões.

## `PermissionCatalog` (novo, `api-key-core`)

Leitura de `api-key.permissions`, compartilhada pela CLI e pela biblioteca:

```java
package dev.leilaalgarve.apikey.core;

public final class PermissionCatalog {
    public boolean isConfigured();          // false = sem catálogo (comportamento 1.1.0)
    public SortedSet<String> names();       // todas, em ordem alfabética
    public SortedSet<String> defaults();    // as marcadas como padrão
    public boolean contains(String name);
}
```

Um catálogo inválido (nome fora do formato, nome repetido, nenhuma permissão padrão) falha na
inicialização do contexto do Spring, com uma exceção própria. A CLI traduz essa exceção para
o exit 2 do contrato de CLI.
