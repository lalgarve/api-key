# Tasks: Refatorar pacote base `io.deployo` → `dev.leilaalgarve`

Quebra `spec.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Esta spec não tem
`plan.md` — não há decisão técnica em aberto para uma mudança puramente mecânica, mesmo
racional já usado em `jogo-acoes` (`specs/05-001-refactor-pacote-base`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Mover `src/main/java` e `src/test/java` de `io/deployo/apikey` para `dev/leilaalgarve/apikey`, ajustando a declaração `package` em cada arquivo | — | | #16 |
| T002 | Atualizar todos os imports `io.deployo.apikey.*` → `dev.leilaalgarve.apikey.*` | T001 | | #16 |
| T003 | Atualizar javadoc/comentários que citem o pacote antigo por nome | T001 | [P] | #16 |
| T004 | Atualizar `groupId` do `pom.xml` de `io.deployo.apikey` para `dev.leilaalgarve.apikey` (`artifactId` não muda) | — | [P] | #16 |
| T005 | Atualizar as menções ao pacote antigo em `specs/002-revoke-api-key/plan.md`, `specs/003-auto-revoke-on-rotation/plan.md` e `specs/004-list-api-keys/plan.md` | — | [P] | #16 |
| T006 | Grep final por `io.deployo` em todo o repositório (fora de `docs/context/diario.md`, registro histórico, e fora de diretórios de build gerados) para achar referências residuais não cobertas pelas tarefas acima, e corrigir | T001, T002, T003, T004, T005 | | #16 |
| T007 | Rodar a suíte completa (`mvn verify`) e confirmar 100% verde, mesma cobertura, sem nenhuma asserção alterada — só pacote muda | T006 | | #16 |
| T008 | Registrar a mudança numa entrada nova de `docs/context/diario.md` (não editar entradas antigas) | T007 | [P] | #16 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
