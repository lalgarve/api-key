# Tasks: auto-revoke-on-rotation

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T000 | Resolver as decisões em aberto de `spec.md` (valor padrão de `N`; existência de opt-out) | — | | #12 |
| T001 | Implementar `ApiKeyRepository.findByClientNameAndRevokedAtIsNullOrRevokedAtGreaterThan` | `002-revoke-api-key` T001 | [P] | #12 |
| T002 | Implementar o colaborador de política de rotação (busca das chaves ativas do cliente, cálculo do novo `revoked_at` sem adiar um agendamento existente, persistência) | T000, T001 | | #12 |
| T003 | Implementar parsing/validação de `--revoke-old-in-days` (opcional, `>= 0`) | T000 | [P] | #12 |
| T004 | Integrar o colaborador de T002 ao fluxo de `GenerateCommand`, dentro da mesma transação da persistência da chave nova | T002, T003 | | #12 |
| T005 | Testes: sem chave antiga (sem efeito colateral); uma chave antiga com carência padrão; carência customizada; `--revoke-old-in-days 0` (imediata); múltiplas chaves antigas; chave antiga já agendada mais cedo (não adiada); valor inválido; falha de persistência (atomicidade: nem a chave nova é salva) | T004 | | #12 |
| T006 | Atualizar README e `specs/001-generate-api-key/contracts/cli-commands.md` (ou linkar a extensão em `contracts/cli-commands.md` desta feature) | T004 | [P] | #12 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Toda esta feature depende de `002-revoke-api-key` estar implementada (coluna `revoked_at` e
  despacho de comandos) antes de começar.
