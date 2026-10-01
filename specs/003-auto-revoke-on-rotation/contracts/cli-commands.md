# Contrato de CLI: auto-revoke-on-rotation

Escrito antes da implementação — a implementação segue o contrato. Este arquivo documenta só
a **diferença** em relação ao contrato de `generate` já publicado em
[`specs/001-generate-api-key/contracts/cli-commands.md`](../../001-generate-api-key/contracts/cli-commands.md);
o restante (argumentos `--client`/`--validity-days`, formato geral, exit codes 0-3 já
existentes) permanece igual.

## Comando: `generate` (estendido)

**Uso:**
```
deployo-api-key generate --client <nome-do-cliente> [--validity-days <dias>] [--revoke-old-in-days <dias>]
```

**Argumento novo:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--revoke-old-in-days` | não | Dias de carência, a partir de agora, até que as chaves já ativas do mesmo cliente sejam revogadas. Inteiro `>= 0` (`0` = revogação imediata). **Se omitido, a rotina inteira não roda** — nenhuma chave existente é lida ou alterada, mesmo que o cliente já tenha uma chave ativa (não existe valor padrão). Sem efeito também se o cliente não tiver nenhuma chave ativa. |

**Saída (stdout), sucesso — cliente sem chave antiga ativa:**

Inalterada em relação a `001` — nenhuma linha extra.

**Saída (stdout), sucesso — cliente com uma ou mais chaves antigas ativas:**
```
API key generated for client 'jogo-acoes' (does not expire).
This is the only time the plaintext key is shown — store it now:

dak_9f2c1a4e7b3d8f0a1c5e6b7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a

1 existing key for client 'jogo-acoes' scheduled for revocation in 7 days (on 2026-10-07T10:00:00Z).
```

Quando mais de uma chave antiga é afetada, a última linha vira plural: `N existing keys for
client 'jogo-acoes' scheduled for revocation...`. Quando `--revoke-old-in-days 0` é usado, a
linha final muda de tempo verbal: `1 existing key for client 'jogo-acoes' has been revoked.`

**Exit codes (novo):**

| Código | Significado |
|---|---|
| 1 | (reaproveitado) Erro de uso — agora também cobre `--revoke-old-in-days` inválido |
| 3 | (reaproveitado) Erro de persistência — agora também cobre falha ao atualizar uma chave antiga (a chave nova também não é persistida, ver FR6) |

**Erros (stderr), novo:**

| Condição | Mensagem | Exit code |
|---|---|---|
| `--revoke-old-in-days` inválido (negativo ou não-numérico) | `Error: --revoke-old-in-days must be zero or a positive integer.` | 1 |
