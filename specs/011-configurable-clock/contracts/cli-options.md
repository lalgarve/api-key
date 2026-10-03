# Contrato de CLI: configurable-clock

Escrito antes da implementação — a implementação segue o contrato. Vale para todos os
comandos (`generate`, `revoke`, `list`, `check`); o restante do contrato de cada comando não
muda.

## Opções globais de relógio

**Uso:**
```
deployo-api-key <comando> [opções do comando] [--clock-start <data> | --clock-offset-days <N>]
```

**Argumentos:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--clock-start` | não | Faz o relógio começar neste instante e avançar a partir dele. Data ISO-8601 (`2026-01-31`, lida como meia-noite UTC) ou instante ISO-8601 em UTC (`2026-01-31T10:00:00Z`). Passado ou futuro. |
| `--clock-offset-days` | não | Desloca o relógio real em N dias. Inteiro com sinal: positivo vai para o futuro, negativo para o passado, `0` não muda nada. |

As duas opções não podem ser usadas juntas, e nenhuma é aceita com o perfil `staging` ou
`production` ativo.

**Saída (stderr), com relógio simulado (antes da saída do comando):**
```
Warning: simulated clock in use; now is 2026-11-03T16:45:02.123Z.
```

Não aparece sem opção de relógio nem com `--clock-offset-days 0`. O stdout de cada comando
fica igual ao seu contrato.

**Exit codes:**

| Código | Significado |
|---|---|
| 1 | Erro de uso de uma opção de relógio (tabela abaixo). Nesse caso o comando não roda e o banco não é lido nem escrito |
| demais | Os do próprio comando, inalterados |

**Erros (stderr), na ordem em que são checados:**

| Condição | Mensagem | Exit code |
|---|---|---|
| Perfil `staging` ou `production` ativo e qualquer opção de relógio presente (inclusive `--clock-offset-days 0`) | `Error: clock options are not allowed in the staging or production environment.` | 1 |
| `--clock-start` e `--clock-offset-days` juntos | `Error: --clock-start and --clock-offset-days cannot be used together.` | 1 |
| `--clock-start` sem valor ou inválido | `Error: --clock-start must be an ISO-8601 date (2026-01-31) or UTC instant (2026-01-31T10:00:00Z).` | 1 |
| `--clock-offset-days` sem valor ou não inteiro | `Error: --clock-offset-days must be an integer (negative, zero or positive).` | 1 |
