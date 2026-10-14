# Contrato de CLI: key-permissions

Escrito antes da implementação — a implementação segue o contrato. Este arquivo documenta só
a **diferença** em relação aos contratos já publicados:

- `generate`: [`001-generate-api-key`](../../001-generate-api-key/contracts/cli-commands.md),
  estendido por [`003-auto-revoke-on-rotation`](../../003-auto-revoke-on-rotation/contracts/cli-commands.md);
- `list`: [`004-list-api-keys`](../../004-list-api-keys/contracts/cli-commands.md);
- `validate`: [`010-validate-api-key-cli`](../../010-validate-api-key-cli/contracts/cli-commands.md).

O restante (argumentos, saídas e exit codes já existentes) não muda. `revoke` não muda.

## Catálogo de permissões

Configuração da instância, lida pela CLI e pela biblioteca de validação (ver FR13 e
"Decisões em aberto", item 1, em `spec.md`). Exemplo para o serviço de e-mail, em `application.yml` ou no
arquivo do perfil:

```yaml
api-key:
  permissions:
    - name: email:send
      default: true
    - name: disposable-email:admin
      default: false
```

| Campo | Obrigatório | Regra |
|---|---|---|
| `name` | sim | `^[a-z][a-z0-9-]*(:[a-z0-9-]+)*$`, no máximo 100 caracteres, único no catálogo |
| `default` | não | `false` se omitido. Pelo menos uma permissão do catálogo tem `default: true` |

Como qualquer propriedade do Spring Boot, também pode vir de variável de ambiente
(`API_KEY_PERMISSIONS_0_NAME=email:send`, `API_KEY_PERMISSIONS_0_DEFAULT=true`, ...).

Sem `api-key.permissions`, ou com a lista vazia, a instância não tem catálogo, e todos os
comandos se comportam como antes desta spec.

**Erro de configuração (stderr), qualquer comando:**

| Condição | Mensagem | Exit code |
|---|---|---|
| Nome fora do formato | `Error: invalid permission name '<nome>' in api-key.permissions.` | 2 |
| Nome repetido | `Error: permission '<nome>' is declared more than once in api-key.permissions.` | 2 |
| Nenhuma permissão padrão | `Error: api-key.permissions must mark at least one permission as default.` | 2 |

O exit 2 é o mesmo do pepper ausente em `001`: erro de configuração da instância, não de uso.

## Comando: `generate` (estendido)

**Uso:**
```
deployo-api-key generate --client <nome-do-cliente> [--validity-days <dias>] [--revoke-old-in-days <dias>] [--permissions <p1>[,<p2>...]]
```

**Argumento novo:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--permissions` | não | Permissões da chave, separadas por vírgula. Espaços nas pontas de cada item são ignorados; itens repetidos contam uma vez. Cada item precisa estar no catálogo. **Se omitido, a chave recebe as permissões padrão do catálogo.** Se informado, a chave recebe só as listadas. Numa instância sem catálogo, não pode ser usado. |

**Saída (stdout), sucesso — instância sem catálogo:**

Inalterada em relação a `001`/`003`.

**Saída (stdout), sucesso — instância com catálogo:**

Uma linha a mais, sempre a última, com as permissões em ordem alfabética:

```
API key generated for client 'email-admin' (does not expire).
This is the only time the plaintext key is shown — store it now:

dak_Jd8c2mQ0xLr7VtP3yBn5KwA1sE9hF6gZ4uT0iO2pY7c

Permissions: disposable-email:admin, email:send.
```

Com `--revoke-old-in-days`, a linha de rotação de `003` vem antes, e `Permissions:` continua
sendo a última:

```
API key generated for client 'jogo-acoes' (does not expire).
This is the only time the plaintext key is shown — store it now:

dak_Jd8c2mQ0xLr7VtP3yBn5KwA1sE9hF6gZ4uT0iO2pY7c

1 existing key for client 'jogo-acoes' scheduled for revocation in 7 days (on 2026-10-21T10:00:00Z).
Permissions: email:send.
```

**Exit codes (reaproveitados):**

| Código | Significado |
|---|---|
| 1 | Erro de uso — agora também cobre `--permissions` inválido |
| 2 | Erro de configuração — agora também cobre catálogo inválido |
| 3 | Erro de persistência — agora também cobre falha ao gravar as permissões (a chave também não é gravada) |

**Erros (stderr), novos:**

| Condição | Mensagem | Exit code |
|---|---|---|
| `--permissions` com um item fora do catálogo (o primeiro, na ordem informada) | `Error: unknown permission '<nome>'. Known permissions: <todas, em ordem alfabética, separadas por ", ">.` | 1 |
| `--permissions` sem nenhum item (vazio, só vírgulas ou só espaços) | `Error: --permissions must list at least one permission.` | 1 |
| `--permissions` numa instância sem catálogo | `Error: --permissions requires a permission catalog. None is configured for this instance.` | 1 |

Em qualquer erro, nenhuma linha é gravada e nenhuma chave em texto puro é impressa (mesma
regra de `001`). As validações de `--permissions` acontecem antes de gerar a chave, junto com
as de `--client` e `--validity-days`.

## Comando: `list` (estendido)

**Uso:** inalterado.

**Saída (stdout), instância sem catálogo:** inalterada em relação a `004`.

**Saída (stdout), instância com catálogo:** coluna nova `PERMISSIONS`, sempre a última, com as
permissões da chave em ordem alfabética, separadas por vírgula sem espaço (a tabela é separada
por espaços):

```
ID  CLIENT       CREATED_AT            EXPIRES_AT            REVOKED_AT            STATUS   PERMISSIONS
3   jogo-acoes   2026-09-01T10:00:00Z  2026-12-01T10:00:00Z  -                     active   email:send
8   email-admin  2026-10-14T09:00:00Z  -                     -                     active   disposable-email:admin,email:send
```

Uma chave emitida antes da 1.1.0 mostra as permissões que o validador devolveria para ela
(FR13 de `spec.md`). Uma chave sem nenhuma permissão efetiva mostra `-`.

Filtros, mensagem de "nenhum resultado" e exit codes não mudam.

## Comando: `validate` (estendido)

**Uso:** inalterado.

**Saída (stdout), chave válida com permissões:**
```
API key is valid for client 'email-admin'.
Permissions: disposable-email:admin, email:send.
```

**Saída (stdout), chave válida sem permissões** (instância sem catálogo): inalterada em
relação a `010`, uma linha só.

Saída de chave inválida e exit codes não mudam.
