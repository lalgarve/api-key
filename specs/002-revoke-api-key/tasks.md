# Tasks: revoke-api-key

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T000 | Resolver as decisões em aberto de `spec.md` (revogar chave já expirada; múltiplas chaves ativas por cliente) | — | | #11 |
| T001 | Criar migration Flyway `V2` acrescentando `revoked_at` a `api_keys` (`data-model.md`) | T000 | [P] | #11 |
| T002 | Refatorar o despacho de comandos da CLI: interface `CliCommand` + tabela de despacho por `args[0]` em `ApiKeyCliRunner` (sem mudar o comportamento observável de `generate`) | T000 | [P] | #11 |
| T003 | Implementar `RevokeCommand` (parsing de `--id`/`--in-days`, validações, busca da chave, aplicar/reagendar `revoked_at`, persistência) | T001, T002 | | #11 |
| T004 | Testes: revogação imediata; revogação agendada; `--id` ausente/não numérico; chave não encontrada; `--in-days` inválido; chave já revogada; reagendamento de uma revogação futura existente; falha de persistência | T003 | | #11 |
| T005 | Documentar o comando `revoke` no README (seção de uso) | T003 | [P] | #11 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- `003-auto-revoke-on-rotation` e `004-list-api-keys` dependem de T001 (coluna `revoked_at`) e
  T002 (despacho de comandos) desta feature — ver os respectivos `plan.md`.
