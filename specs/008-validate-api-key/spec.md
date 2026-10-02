# Spec: validate-api-key

**Status:** rascunho
**Issue:** #23

## Resumo

Uma biblioteca de leitura — sem CLI, sem escrita no banco — que o próprio serviço protegido
(ex.: o serviço de e-mail citado no README) embute para validar, a cada requisição recebida,
se uma API-KEY em texto puro apresentada por um chamador é válida. Reaproveita a mesma base de
dados já populada por `generate`/`revoke`, rodando no mesmo container/banco dessa instância
(ver README, "Contexto e propósito"). Devolve um resultado tipado — chave válida com o nome do
cliente, ou inválida com o motivo exato — nunca um `boolean` cru, para quem chama decidir o que
fazer com cada motivo (logar, responder, expor ou não ao chamador externo).

## Motivação

Esta feature foi explicitamente adiada desde `001-generate-api-key` ("Fora de escopo": "A
biblioteca de leitura usada pelo serviço protegido... para validar chaves recebidas de
clientes — feature futura separada"). Sem ela, o projeto só sabe emitir e gerenciar chaves —
não há como o lado que *recebe* uma chamada autenticada por API-KEY (o serviço protegido em
si) confirmar que a chave apresentada é real, ainda ativa, e descobrir de qual cliente ela é.

Devolver um `boolean` simples (`isValid(key)`) jogaria fora informação que o chamador
normalmente precisa: differenciar "chave nunca existiu" de "chave revogada" de "chave expirada"
muda o que a aplicação cliente deveria fazer (tentar de novo mais tarde não ajuda numa chave
revogada, por exemplo) e o que vale a pena logar para investigação. Um resultado tipado com o
motivo resolve isso sem forçar a biblioteca a decidir sozinha o que logar ou responder.

## Cenários (comportamento esperado)

Executáveis em [`features/validate-api-key.feature`](../../src/test/resources/features/validate-api-key.feature)
(roda com `mvn verify`, junto com a suíte JUnit — ver `specs/006-executable-gherkin-scenarios`).

- validate with no key presented (ausente/em branco)
- validate a key with the wrong format (prefixo ou tamanho errados)
- validate a key that was never issued (hash não encontrado)
- validate a revoked key
- validate an expired key
- validate a key that is both expired and revoked (revogada tem prioridade — mesma regra de `004-list-api-keys` FR1)
- validate a currently active key (caminho feliz)

## Requisitos funcionais

- FR1: Uma nova classe `ApiKeyValidator` expõe `validate(String rawKey)`, devolvendo um
  `ApiKeyValidationResult` — nunca lança exceção para uma chave ausente/malformada/inexistente/
  revogada/expirada (essas são resultados esperados do domínio, não erros de sistema).
- FR2: `ApiKeyValidationResult` é uma `sealed interface` com dois `record`s: `Valid(String
  clientName)` e `Invalid(ApiKeyFailureReason reason)`.
- FR3: `ApiKeyFailureReason` é um enum só com o código do motivo — `MISSING`, `MALFORMED`,
  `NOT_FOUND`, `EXPIRED`, `REVOKED` — sem mensagem nem status HTTP embutidos (ver "Decisões em
  aberto" em `plan.md`: isso é responsabilidade de quem consome a biblioteca).
  - Nota sobre nomenclatura: o motivo para "hash não corresponde a nenhuma chave emitida" se
    chama `NOT_FOUND`, não `INVALID` — `ApiKeyValidationResult.Invalid` já é o nome do
    resultado-envelope; um motivo chamado `INVALID` dentro de um `Invalid` seria redundante.
- FR4: Validação, em ordem:
  1. `rawKey` nulo ou em branco → `MISSING`.
  2. `rawKey` não bate com o formato exato gerado por `ApiKeyGenerator` (prefixo `dak_` seguido
     de 43 caracteres base64url sem padding) → `MALFORMED`. Isto evita calcular hash e consultar
     o banco para qualquer string que este projeto nunca poderia ter gerado.
  3. Hash do `rawKey` (reaproveitando `ApiKeyHasher` já existente) não corresponde a nenhuma
     linha em `api_keys` → `NOT_FOUND`.
  4. Chave encontrada e revogada (`ApiKey.isRevoked(Instant.now())`, já existente) → `REVOKED`
     — checado antes de `EXPIRED`, mesma prioridade de `004-list-api-keys` FR1.
  5. Chave encontrada, não revogada, e expirada (`ApiKey.isExpired(Instant.now())`, já
     existente) → `EXPIRED`.
  6. Caso contrário → `Valid(clientName)`.
- FR5: `ApiKeyRepository` ganha `Optional<ApiKey> findByKeyHash(String keyHash)` — consulta
  indexada, já que `key_hash` tem `unique = true` desde `001-generate-api-key`.
- FR6: A chave em texto puro (`rawKey`) nunca é logada por este validador, em nenhum branch —
  mesmo princípio já estabelecido em `001-generate-api-key` ("a chave em texto puro nunca
  aparece em log de aplicação"). Só o hash ou o motivo podem aparecer em log, nunca a chave.
- FR7: `ApiKeyValidator` não decide o que fazer com o resultado (não loga, não monta resposta
  HTTP, não aplica nenhuma política de "quanto expor") — só devolve o `ApiKeyValidationResult`
  tipado. Toda decisão de apresentação/transporte é de quem consome a biblioteca.

## Requisitos não-funcionais

- Nenhuma mudança de schema — leitura somente contra a tabela `api_keys` já existente, sem
  nova migration Flyway.
- `findByKeyHash` é uma consulta por índice único — custo constante por chamada,
  independente do número de chaves já emitidas.

## Fora de escopo

- Qualquer integração HTTP/servlet pronta (filtro, `ExceptionHandler`, formato de resposta
  JSON) — fica com quem consome a biblioteca. Ver "Decisões em aberto" em `plan.md`.
- `RATE_LIMITED` como motivo de validação — limite de requisições não é uma propriedade da
  chave em si, é política de tráfego de quem consome a biblioteca; misturar as duas coisas no
  mesmo motivo confundiria responsabilidades diferentes. Ver "Decisões em aberto".
- Qualquer política de "quanto detalhe expor ao chamador externo" (ex.: agrupar `REVOKED`/
  `NOT_FOUND` como uma única resposta genérica) — a biblioteca sempre devolve o motivo exato;
  agrupar ou não é decisão de cada consumidor.
- Publicar este artefato num repositório Maven externo — por enquanto, consumido só dentro
  deste mesmo workspace.
- Qualquer cache de resultado de validação além do índice único já existente em `key_hash`.

## Decisões em aberto

Ver `plan.md`, seção "Decisões de arquitetura" — duas decisões reais seguem em aberto,
aguardando confirmação antes de implementar:

1. Módulo Maven separado para esta biblioteca (como `001-generate-api-key/plan.md` já
   cogitava) vs. manter no mesmo artefato, numa nova package `dev.leilaalgarve.apikey.validation`.
2. Deixar de fora, por ora, a integração HTTP pronta (filtro Spring) e o motivo `RATE_LIMITED`
   — ou incluir algum dos dois já nesta spec.
