# Tasks: auto-revoke-on-rotation

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T000~~ | Resolver as decisões em aberto de `spec.md` — resolvida: `--revoke-old-in-days` é opcional e sem valor padrão; omiti-lo é o próprio mecanismo de opt-out | — | | #12 |
| T001 | Implementar `ApiKeyRepository.findByClientNameAndRevokedAtIsNullOrRevokedAtGreaterThan` | `002-revoke-api-key` T001 | [P] | #12 |
| T002 | Implementar o colaborador de política de rotação (busca das chaves ativas do cliente, cálculo do novo `revoked_at` sem adiar um agendamento existente, persistência) | T001 | | #12 |
| T003 | Implementar parsing/validação de `--revoke-old-in-days` (opcional; quando ausente, a rotina inteira é pulada; quando presente, `>= 0`) | — | [P] | #12 |
| T004 | Integrar o colaborador de T002 ao fluxo de `GenerateCommand`, dentro da mesma transação da persistência da chave nova | T002, T003 | | #12 |
| T005 | Testes: sem `--revoke-old-in-days` e com chave antiga (sem efeito colateral, mesmo comportamento de `001`); com `--revoke-old-in-days` e sem chave antiga (sem efeito); carência explícita; `--revoke-old-in-days 0` (imediata); múltiplas chaves antigas; chave antiga já agendada mais cedo (não adiada); valor inválido; falha de persistência (atomicidade: nem a chave nova é salva) | T004 | | #12 |
| T006 | Atualizar README e `specs/001-generate-api-key/contracts/cli-commands.md` (ou linkar a extensão em `contracts/cli-commands.md` desta feature) | T004 | [P] | #12 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Toda esta feature depende de `002-revoke-api-key` estar implementada (coluna `revoked_at` e
  despacho de comandos) antes de começar.
