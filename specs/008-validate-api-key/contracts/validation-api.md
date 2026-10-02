# Contrato de API: validate-api-key

Escrito antes da implementação — a implementação segue o contrato. Equivalente a um contrato
OpenAPI, mas para a API pública Java desta biblioteca (`dev.leilaalgarve.apikey.validation`).

## `ApiKeyFailureReason`

```java
public enum ApiKeyFailureReason {
    MISSING,
    MALFORMED,
    NOT_FOUND,
    EXPIRED,
    REVOKED
}
```

Sem métodos, sem status HTTP nem mensagem embutidos — ver `plan.md`, "Decisões de
arquitetura". Mapear cada valor para uma resposta concreta (status, texto, idioma, quanto
detalhe expor) é responsabilidade de quem consome a biblioteca.

## `ApiKeyValidationResult`

```java
public sealed interface ApiKeyValidationResult {
    record Valid(String clientName) implements ApiKeyValidationResult {}
    record Invalid(ApiKeyFailureReason reason) implements ApiKeyValidationResult {}
}
```

- `Valid.clientName()` é o mesmo valor já usado em `--client` na CLI (`ApiKey.getClientName()`)
  — nunca o `id` nem o hash da chave.
- `Invalid.reason()` nunca é nulo.

## `ApiKeyValidator`

```java
package dev.leilaalgarve.apikey.validation;

public class ApiKeyValidator {
    public ApiKeyValidator(ApiKeyRepository repository, ApiKeyHasher hasher) { ... }

    public ApiKeyValidationResult validate(String rawKey) { ... }
}
```

**Contrato de `validate`:**

| Entrada | Saída |
|---|---|
| `null` ou `rawKey.isBlank()` | `Invalid(MISSING)` |
| Não corresponde a `^dak_[A-Za-z0-9_-]{43}$` | `Invalid(MALFORMED)` |
| Hash (via `ApiKeyHasher.hash`) não encontrado em `api_keys` | `Invalid(NOT_FOUND)` |
| Encontrada e `isRevoked(Instant.now())` | `Invalid(REVOKED)` |
| Encontrada, não revogada, e `isExpired(Instant.now())` | `Invalid(EXPIRED)` |
| Encontrada, não revogada, não expirada | `Valid(clientName)` |

- Nunca lança exceção para nenhuma das linhas acima — são resultados esperados do domínio.
  Exceções seguem reservadas para falhas de infraestrutura reais (ex.: banco indisponível,
  `MissingHmacPepperException` já existente se o pepper não estiver configurado).
- Nunca loga `rawKey` em nenhum branch (FR6 de `spec.md`).

## Alterações num contrato já existente

`ApiKeyRepository` muda de package (`dev.leilaalgarve.apikey.issuance` →
`dev.leilaalgarve.apikey.core`, módulo `api-key-core` — ver `plan.md`, "Estrutura de
módulos/pacotes") e ganha:

```java
Optional<ApiKey> findByKeyHash(String keyHash);
```

Sem remover nem alterar nenhum método já existente. `ApiKey`, `ApiKeyHasher` e
`MissingHmacPepperException` mudam para a mesma package, sem alteração de assinatura.

## `ApiKeyFormat` (novo, `api-key-core`)

```java
package dev.leilaalgarve.apikey.core;

public final class ApiKeyFormat {
    public static final String PREFIX = "dak_";
    public static final int ENTROPY_BYTES = 32;
    public static boolean matches(String candidate) { ... } // ^dak_[A-Za-z0-9_-]{43}$
}
```

Fonte única do formato da chave: `ApiKeyGenerator` (na CLI) usa `PREFIX`/`ENTROPY_BYTES` para
gerar, `ApiKeyValidator` usa `matches` para o ramo `MALFORMED`.
