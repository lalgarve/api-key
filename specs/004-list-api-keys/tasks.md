# Tasks: list-api-keys

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T000 | Resolver a decisão em aberto de `spec.md` (chave expirada aparecendo como "active") | — | | #13 |
| T001 | Implementar as consultas necessárias em `ApiKeyRepository` (`findAll`, `findByClientName`) | `002-revoke-api-key` T001 | [P] | #13 |
| T002 | Implementar `ListCommand` (parsing de `--status`/`--client`/`--revoking-within-days`, validações, filtragem em memória, formatação da tabela) | T000, T001, `002-revoke-api-key` T002 | | #13 |
| T003 | Testes: padrão (só ativas); `--status all`/`revoked`; `--client`; `--revoking-within-days` dentro/fora da janela; combinação inválida com `--status`; lista vazia; valores inválidos | T002 | | #13 |
| T004 | Documentar o comando `list` no README (seção de uso) | T002 | [P] | #13 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Depende de `002-revoke-api-key` estar implementada (coluna `revoked_at`, despacho de
  comandos). Não depende de `003-auto-revoke-on-rotation`.
