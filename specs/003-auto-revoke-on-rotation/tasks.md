# Tasks: auto-revoke-on-rotation

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T000~~ | Resolver as decisões em aberto de `spec.md` — resolvida: `--revoke-old-in-days` é opcional e sem valor padrão; omiti-lo é o próprio mecanismo de opt-out | — | | #12 |
| ~~T001~~ | Implementar a busca das chaves ativas de um cliente (`ApiKeyRepository.findByClientName` + filtro em memória — ver `plan.md` sobre a armadilha do método derivado original) | `002-revoke-api-key` T001 | [P] | #12 |
| ~~T002~~ | Implementar o colaborador de política de rotação (`OldKeyRotationPolicy`: busca das chaves ativas do cliente, cálculo do novo `revoked_at` sem adiar um agendamento existente, persistência) | T001 | | #12 |
| ~~T003~~ | Implementar parsing/validação de `--revoke-old-in-days` (opcional; quando ausente, a rotina inteira é pulada; quando presente, `>= 0`) | — | [P] | #12 |
| ~~T004~~ | Integrar o colaborador de T002 ao fluxo de `GenerateCommand`, **antes** de persistir a chave nova (ver `plan.md`), dentro da mesma transação | T002, T003 | | #12 |
| ~~T005~~ | Testes: sem `--revoke-old-in-days` e com chave antiga (sem efeito colateral, mesmo comportamento de `001`); com `--revoke-old-in-days` e sem chave antiga (sem efeito); carência explícita; `--revoke-old-in-days 0` (imediata); múltiplas chaves antigas; chave antiga já agendada mais cedo (não adiada); valor inválido; falha de persistência (atomicidade: nem a chave nova é salva, provado com `@MockitoSpyBean`, não um mock completo) | T004 | | #12 |
| ~~T006~~ | Atualizar README e linkar a extensão do contrato de `generate` | T004 | [P] | #12 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Todas as tarefas desta feature estão concluídas.
