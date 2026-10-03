# Contrato de CLI: validate-api-key-cli

Escrito antes da implementação — a implementação segue o contrato.

## Comando: `validate`

**Uso:**
```
deployo-api-key validate < arquivo-com-a-chave
```

**Argumentos:** nenhum. A chave é lida da entrada padrão — só a primeira linha, sem espaços
nas pontas. Argumentos extras são ignorados (mesmo comportamento dos demais comandos).

**Saída (stdout), chave válida:**
```
API key is valid for client 'jogo-acoes'.
```

**Exit codes:**

| Código | Significado | `ApiKeyFailureReason` |
|---|---|---|
| 0 | Chave válida agora | — |
| 1 | Erro de uso: nenhuma chave na entrada padrão (vazia ou só espaços) | `MISSING` |
| 2 | Formato inválido — não pode ter sido gerada por esta ferramenta | `MALFORMED` |
| 3 | Nenhuma chave emitida corresponde | `NOT_FOUND` |
| 4 | Chave revogada (revogação já em vigor) | `REVOKED` |
| 5 | Chave expirada | `EXPIRED` |

**Saída (stderr), chave inválida ou ausente:**

| Condição | Mensagem | Exit code |
|---|---|---|
| Entrada padrão vazia | `Error: no API key provided on stdin.` | 1 |
| Formato inválido | `Invalid: the key is malformed.` | 2 |
| Nenhuma chave corresponde | `Invalid: no API key matches.` | 3 |
| Chave revogada | `Invalid: the key was revoked.` | 4 |
| Chave expirada | `Invalid: the key expired.` | 5 |

Nenhuma mensagem inclui a chave informada. Uma chave revogada e expirada ao mesmo tempo sai
com exit 4 (revogada tem prioridade — `008-validate-api-key` FR4). Uma chave com revogação
agendada ainda não em vigor é válida (exit 0). O comando nunca altera o banco.
