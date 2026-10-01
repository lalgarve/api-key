# Contrato de CLI: revoke-api-key

Escrito antes da implementação — a implementação segue o contrato.

## Comando: `revoke`

**Uso:**
```
deployo-api-key revoke --id <id> [--in-days <dias>]
```

**Argumentos:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--id` | sim | Identificador numérico da chave a revogar (ver `specs/004-list-api-keys` para como descobri-lo). |
| `--in-days` | não | Número de dias, a partir de agora, até a revogação entrar em vigor. Inteiro positivo (> 0). Se omitido, a revogação é imediata. |

**Saída (stdout), sucesso — revogação imediata:**
```
API key 3 for client 'jogo-acoes' has been revoked.
```

**Saída (stdout), sucesso — revogação agendada:**
```
API key 3 for client 'jogo-acoes' will be revoked in 14 days (on 2026-10-14T10:00:00Z).
```

**Exit codes:**

| Código | Significado |
|---|---|
| 0 | Sucesso — revogação aplicada ou agendada |
| 1 | Erro de uso: `--id` ausente ou não numérico; `--in-days` inválido; `--in-days` agendaria a revogação depois de `expires_at` |
| 2 | Chave não encontrada para o `--id` informado |
| 3 | A chave já está revogada (momento de revogação já no passado) |
| 4 | Erro de persistência: falha ao gravar no banco de dados |
| 5 | A chave já expirou (`expires_at` já no passado) — nada a revogar |

**Erros (stderr):**

| Condição | Mensagem | Exit code |
|---|---|---|
| `--id` ausente | `Error: --id is required.` | 1 |
| `--id` não numérico | `Error: --id must be a number.` | 1 |
| `--in-days` inválido (zero, negativo ou não-numérico) | `Error: --in-days must be a positive integer.` | 1 |
| `--in-days` agendaria a revogação depois de `expires_at` | `Error: --in-days would schedule the revocation after the key already expires (expires at <expires_at>).` | 1 |
| Nenhuma chave com esse `--id` | `Error: no API key found with id <id>.` | 2 |
| Chave já revogada | `Error: API key <id> is already revoked.` | 3 |
| Falha ao persistir no banco | `Error: could not revoke the key. No change was saved.` | 4 |
| Chave já expirada | `Error: API key <id> already expired on <expires_at>; nothing to revoke.` | 5 |

Em qualquer caso de erro, nenhuma linha é alterada no banco de dados. Uma chave já revogada
(exit 3) é verificada antes de uma chave já expirada (exit 5) — ver `plan.md` para a ordem
completa de verificação.
