# Tasks: validate-api-key

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T000~~ | Resolver as decisões em aberto em `plan.md` (módulo Maven separado vs. mesmo artefato; incluir ou não `RATE_LIMITED`/filtro HTTP) — vira commit `decision:`, atualizando a tabela de `plan.md` | — | | #23 |
| T001 | `refactor:` converter `pom.xml` em POM agregador com os módulos `api-key-core` e `api-key-cli`; mover todo o código/testes/recursos atuais para `api-key-cli`, `spring-boot-maven-plugin` só nele; ajustar caminhos do relatório JaCoCo no CI | T000 | | #23 |
| T002 | `refactor:` mover `ApiKey`, `ApiKeyRepository`, `ApiKeyHasher`, `MissingHmacPepperException` e as migrations (`db/migration`, `db/migration-h2`) para `api-key-core`, package `dev.leilaalgarve.apikey.core`, com os testes correspondentes | T001 | | #23 |
| T003 | `refactor:` extrair `ApiKeyFormat` (prefixo, bytes de entropia, `matches`) para `api-key-core`; `ApiKeyGenerator` passa a usá-lo, sem mudar a chave gerada | T002 | | #23 |
| T004 | Confirmar `mvn clean verify` verde depois de T001-T003, com o mesmo número de testes de antes do refactor; atualizar caminho do jar no README se mudou | T003 | | #23 |
| T005 | Criar módulo `api-key-validation` (depende de `api-key-core`; Flyway + H2 só em escopo de teste) | T004 | | #23 |
| T006 | `ApiKeyRepository.findByKeyHash(String): Optional<ApiKey>` | T004 | [P] | #23 |
| T007 | `ApiKeyFailureReason` (enum: `MISSING`, `MALFORMED`, `NOT_FOUND`, `EXPIRED`, `REVOKED`) | T005 | [P] | #23 |
| T008 | `ApiKeyValidationResult` (`sealed interface` + `record`s `Valid`/`Invalid`) | T007 | [P] | #23 |
| T009 | `ApiKeyValidator.validate(String)` — formato, hash/lookup, precedência revogada-antes-de-expirada, `now` via `Clock` injetado (fallback `Clock.systemUTC()`), nunca loga a chave em texto puro | T006, T008 | | #23 |
| T010 | Testes unitários de `ApiKeyValidator` com `Clock.fixed` cobrindo cada ramo de `contracts/validation-api.md`, incluindo a chave simultaneamente expirada e revogada, as bordas exatas de `expires_at`/`revoked_at`, e uma asserção de que `rawKey` nunca aparece em nenhuma linha de log | T009 | | #23 |
| T011 | `features/validate-api-key.feature` + step definitions em `api-key-validation` (reaproveitando a infraestrutura Cucumber de `006-executable-gherkin-scenarios`) | T009 | | #23 |
| T012 | Revisar `http-integration.md` contra a API final de `ApiKeyValidator` e linkar o guia no README | T009 | [P] | #23 |
| T013 | Rodar `mvn clean verify` e confirmar 100% verde (JUnit + Cucumber) em todos os módulos, mesma cobertura mínima de 80% por módulo | T010, T011 | | #23 |
| T014 | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T013 | [P] | #23 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- T001-T004 são o refactor multi-módulo, sem mudança de comportamento — cada um vira commit
  `refactor:` próprio, antes de qualquer código novo de validação.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
