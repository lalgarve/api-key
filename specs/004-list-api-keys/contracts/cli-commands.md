# Contrato de CLI: list-api-keys

Escrito antes da implementação — a implementação segue o contrato.

## Comando: `list`

**Uso:**
```
deployo-api-key list [--status active|revoked|all] [--client <nome>] [--revoking-within-days <dias>]
```

**Argumentos:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--status` | não | `active` (padrão), `revoked` ou `all`. |
| `--client` | não | Filtra pelo nome exato do cliente. |
| `--revoking-within-days` | não | Mostra só chaves ativas com revogação agendada dentro de N dias a partir de agora. Inteiro positivo (> 0). Só pode ser usado com `--status` omitido ou `--status active`. |

**Saída (stdout), sucesso — com resultados:**
```
ID  CLIENT       CREATED_AT            EXPIRES_AT            REVOKED_AT            STATUS
3   jogo-acoes   2026-09-01T10:00:00Z  2026-12-01T10:00:00Z  -                     active
5   jogo-acoes   2026-09-15T09:30:00Z  -                     2026-10-07T09:30:00Z  active
7   jogo-acoes   2026-08-01T08:00:00Z  -                     2026-08-10T08:00:00Z  revoked
```

`REVOKED_AT` mostra `-` quando nulo; quando preenchido no futuro, a chave continua com
`STATUS = active` (revogação agendada, ainda não em vigor) — ver `002-revoke-api-key`.

**Saída (stdout), sucesso — sem resultados:**
```
No API keys found for the given filters.
```

**Exit codes:**

| Código | Significado |
|---|---|
| 0 | Sucesso — com ou sem resultados |
| 1 | Erro de uso: valor inválido para `--status`; `--revoking-within-days` inválido; ou `--revoking-within-days` combinado com `--status revoked`/`--status all` |

**Erros (stderr):**

| Condição | Mensagem | Exit code |
|---|---|---|
| `--status` com valor desconhecido | `Error: --status must be one of: active, revoked, all.` | 1 |
| `--revoking-within-days` inválido (zero, negativo ou não-numérico) | `Error: --revoking-within-days must be a positive integer.` | 1 |
| `--revoking-within-days` combinado com `--status` diferente de `active` | `Error: --revoking-within-days can only be used with --status active.` | 1 |
