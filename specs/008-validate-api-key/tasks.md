# Tasks: validate-api-key

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T000 | Resolver as decisões em aberto em `plan.md` (módulo Maven separado vs. mesmo artefato; incluir ou não `RATE_LIMITED`/filtro HTTP) — vira commit `decision:`, atualizando a tabela de `plan.md` | — | | #23 |
| T001 | `ApiKeyRepository.findByKeyHash(String): Optional<ApiKey>` | T000 | [P] | #23 |
| T002 | `ApiKeyFailureReason` (enum: `MISSING`, `MALFORMED`, `NOT_FOUND`, `EXPIRED`, `REVOKED`) | T000 | [P] | #23 |
| T003 | `ApiKeyValidationResult` (`sealed interface` + `record`s `Valid`/`Invalid`) | T002 | [P] | #23 |
| T004 | `ApiKeyValidator.validate(String)` — formato, hash/lookup, precedência revogada-antes-de-expirada, nunca loga a chave em texto puro | T001, T003 | | #23 |
| T005 | Testes unitários de `ApiKeyValidator` cobrindo cada ramo de `contracts/validation-api.md`, incluindo a chave simultaneamente expirada e revogada, e uma asserção de que `rawKey` nunca aparece em nenhuma linha de log | T004 | | #23 |
| T006 | `features/validate-api-key.feature` + step definitions (reaproveitando a infraestrutura Cucumber de `006-executable-gherkin-scenarios`) | T004 | | #23 |
| T007 | Rodar `mvn clean verify` e confirmar 100% verde (JUnit + Cucumber), mesma cobertura mínima de 80% | T005, T006 | | #23 |
| T008 | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T007 | [P] | #23 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- T001-T008 ficam bloqueadas por T000 — nenhuma delas começa antes das decisões em aberto de
  `plan.md` serem confirmadas/resolvidas.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
