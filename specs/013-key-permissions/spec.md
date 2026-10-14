# Spec: key-permissions

**Status:** aprovada — não implementada
**Issue:** #14
**Versão alvo:** 1.1.0 (versão menor, compatível com 1.0.x), junto com `010-validate-api-key-cli`

## Resumo

`generate` passa a aceitar `--permissions`, uma lista de permissões que a chave recebe. As
permissões possíveis são definidas por instância, num catálogo de configuração, e uma ou mais
delas são marcadas como padrão. Sem `--permissions`, a chave recebe as permissões padrão. Com
`--permissions`, recebe só as listadas. A biblioteca de validação passa a devolver as permissões
da chave válida, para o serviço protegido decidir o que o chamador pode fazer.

## Motivação

Hoje uma API-KEY só prova quem é o chamador (`client_name`). Ela dá a esse cliente tudo o que o
serviço protegido permite a qualquer chamador autenticado. Não há como restringir o que uma
chave pode fazer dentro do serviço.

O serviço de e-mail precisa disso agora. Ele vai ter duas funções com públicos diferentes:

- a função que já existe: mudar o próprio template e enviar e-mails (o que o `jogo-acoes` usa);
- uma nova: administrar os e-mails descartáveis.

Uma chave emitida para o `jogo-acoes` não deve administrar e-mails descartáveis só porque é uma
chave válida.

### Por que compatível

A 1.0.0 e a 1.0.1 já saíram, e as bibliotecas `api-key-core` e `api-key-validation` estão
publicadas no GitHub Packages. Por isso esta feature entra numa versão menor, a 1.1.0, junto
com o `validate` de `010-validate-api-key-cli`: quem não configurar permissões não percebe
diferença nenhuma.

Isso é uma escolha desta spec, mais restrita do que a seção "Status do sistema: pré-produção"
de `memory/constitution.md` exige. A constitution continua dizendo que o sistema está em
pré-produção, e esta spec não a altera.

A compatibilidade vem da regra do padrão:

- Instância sem catálogo configurado: tudo se comporta como antes desta spec (`generate`,
  `revoke` e `list` como na 1.0.x; `validate` como em `010`). Nenhuma linha de saída
  muda, e o validador devolve um conjunto vazio de permissões.
- Instância com catálogo, `generate` sem `--permissions`: a chave recebe as permissões padrão.
  O `jogo-acoes`, por exemplo, continua gerando a chave com o mesmo comando de antes e recebe a
  permissão da função anterior.
- Instância com catálogo, `generate` com `--permissions`: a chave recebe só as listadas.

## Cenários (comportamento esperado)

Executáveis num `features/key-permissions.feature` novo em `api-key-cli` (ver
`specs/006-executable-gherkin-scenarios`) e na suíte caixa-preta de
`012-blackbox-cli-tests`. Escritos antes do código, em inglês. O catálogo dos cenários é o do
serviço de e-mail:

| Permissão | Padrão | Para quê |
|---|---|---|
| `email:send` | sim | Mudar o próprio template e enviar e-mails (a função anterior) |
| `disposable-email:admin` | não | Administrar os e-mails descartáveis |

Os nomes são um exemplo. Quem escolhe os nomes é o serviço de e-mail, ao configurar a própria
instância.

```gherkin
Feature: Per-key permissions

  Rule: instance with a permission catalog

    Background:
      Given the permission catalog is:
        | permission             | default |
        | email:send             | yes     |
        | disposable-email:admin | no      |

    Scenario: generate without --permissions grants the default permissions
      When the operator runs "generate --client jogo-acoes"
      Then the exit code is 0
      And the last line of stdout is "Permissions: email:send."
      And the new key has exactly the permissions "email:send"

    Scenario: generate with --permissions grants only the listed permissions
      When the operator runs "generate --client email-admin --permissions disposable-email:admin"
      Then the exit code is 0
      And the last line of stdout is "Permissions: disposable-email:admin."
      And the new key has exactly the permissions "disposable-email:admin"

    Scenario: generate with several permissions
      When the operator runs "generate --client email-admin --permissions email:send,disposable-email:admin"
      Then the exit code is 0
      And the new key has exactly the permissions "disposable-email:admin, email:send"

    Scenario: a repeated permission is granted once
      When the operator runs "generate --client jogo-acoes --permissions email:send,email:send"
      Then the exit code is 0
      And the new key has exactly the permissions "email:send"

    Scenario: generate with a permission that is not in the catalog
      When the operator runs "generate --client jogo-acoes --permissions email:delete"
      Then the exit code is 1
      And stderr is "Error: unknown permission 'email:delete'. Known permissions: disposable-email:admin, email:send."
      And no key is saved

    Scenario: generate with an empty --permissions
      When the operator runs "generate --client jogo-acoes --permissions ,"
      Then the exit code is 1
      And stderr is "Error: --permissions must list at least one permission."
      And no key is saved

    Scenario: validate shows the permissions of a valid key
      Given client "email-admin" has an active key with the permissions "disposable-email:admin"
      When the operator runs "validate" with that key on stdin
      Then the exit code is 0
      And stdout is:
        """
        API key is valid for client 'email-admin'.
        Permissions: disposable-email:admin.
        """

    Scenario: list shows a permissions column
      Given client "jogo-acoes" has an active key with the permissions "email:send"
      When the operator runs "list"
      Then the exit code is 0
      And the last column of the header is "PERMISSIONS"
      And the row of that key ends with "email:send"

    Scenario: a key issued before 1.1.0 gets the default permissions
      Given client "jogo-acoes" has an active key issued before 1.1.0
      When the protected service validates that key
      Then the result is valid with the permissions "email:send"

  Rule: instance without a permission catalog (behaviour before this feature)

    Background:
      Given no permission catalog is configured

    Scenario: generate behaves exactly as in 1.0.x
      When the operator runs "generate --client jogo-acoes"
      Then the exit code is 0
      And stdout is the same as in 1.0.x, with no "Permissions:" line

    Scenario: --permissions is rejected
      When the operator runs "generate --client jogo-acoes --permissions email:send"
      Then the exit code is 1
      And stderr is "Error: --permissions requires a permission catalog. None is configured for this instance."
      And no key is saved

    Scenario: list and validate behave exactly as before this feature
      Given client "jogo-acoes" has an active key
      When the operator runs "list"
      Then the header has no "PERMISSIONS" column
      When the operator runs "validate" with that key on stdin
      Then stdout is "API key is valid for client 'jogo-acoes'."

    Scenario: the validator returns no permissions
      Given client "jogo-acoes" has an active key
      When the protected service validates that key
      Then the result is valid with no permissions
```

## Requisitos funcionais

### Catálogo de permissões

- FR1: Cada instância tem seu próprio catálogo de permissões, lido da configuração
  (`api-key.permissions`, formato em
  [`contracts/cli-commands.md`](contracts/cli-commands.md#catálogo-de-permissões)). Segue o
  modelo de uma instância por serviço protegido (README): o catálogo do serviço de e-mail só
  existe na instância do serviço de e-mail.
- FR2: Um nome de permissão segue `^[a-z][a-z0-9-]*(:[a-z0-9-]+)*$` e tem no máximo 100
  caracteres. Exemplos: `email:send`, `disposable-email:admin`. O formato `recurso:ação` é uma
  recomendação; a ferramenta só valida a expressão regular e não interpreta as partes.
- FR3: Um catálogo configurado tem pelo menos uma permissão marcada como padrão. Catálogo com
  nome inválido, nome repetido ou sem nenhuma permissão padrão é erro de configuração: qualquer
  comando da CLI sai com exit 2 antes de fazer qualquer coisa (mesmo código do pepper ausente,
  ver `001-generate-api-key`).
- FR4: Catálogo ausente ou vazio significa "instância sem permissões". Nesse caso a CLI e a
  biblioteca se comportam exatamente como antes desta spec (ver "Por que compatível").

### `generate`

- FR5: Nova opção `--permissions <p1>[,<p2>...]`, lista separada por vírgula, sem espaços
  obrigatórios (espaços nas pontas de cada item são ignorados). Contrato em
  [`contracts/cli-commands.md`](contracts/cli-commands.md).
- FR6: Sem `--permissions`, a chave recebe todas as permissões padrão do catálogo. Com
  `--permissions`, recebe só as listadas, sem somar as padrão. Itens repetidos contam uma vez.
- FR7: Qualquer item fora do catálogo faz o comando falhar com exit 1. Nenhuma chave é
  gravada e nenhuma chave em texto puro é impressa. O mesmo vale para `--permissions` vazio e
  para `--permissions` numa instância sem catálogo.
- FR8: A chave e as suas permissões são gravadas na mesma transação. Se gravar as permissões
  falhar, a chave também não é gravada (exit 3, como em `001`). Isso inclui a revogação das
  chaves antigas de `003-auto-revoke-on-rotation`, que já roda na mesma transação.
- FR9: Quando a chave tem permissões, a saída ganha uma última linha
  `Permissions: <p1>, <p2>.`, em ordem alfabética. A linha fica depois de todas as linhas que
  já existem, inclusive a da rotação de `003`, para nenhuma delas mudar de posição.
- FR10: As permissões de uma chave não mudam depois de geradas. Para trocar as permissões de um
  cliente, gera-se uma chave nova e revoga-se a antiga (`--revoke-old-in-days`, de `003`).

### Validação (`api-key-validation`)

- FR11: `ApiKeyValidationResult.Valid` passa a ter as permissões da chave, como um conjunto
  imutável de nomes, e um método `hasPermission(String)`. Contrato em
  [`contracts/validation-api.md`](contracts/validation-api.md).
- FR12: A biblioteca só informa as permissões; quem decide se a chamada é permitida é o serviço
  protegido. `validate` não ganha um parâmetro de permissão exigida, e
  `ApiKeyFailureReason` não ganha valor novo. Assim nenhum `switch` exaustivo de quem já
  consome a biblioteca deixa de compilar. Que resposta dar quando falta a permissão (ex.: HTTP
  403) é decisão do serviço, como já é para os motivos de falha de `008-validate-api-key`.
- FR13: Uma chave sem nenhuma permissão gravada (emitida antes da 1.1.0) é tratada como se
  tivesse as permissões padrão do catálogo da instância. Sem catálogo, o conjunto é vazio. O
  serviço protegido lê o mesmo catálogo (`api-key.permissions`), como já lê o pepper. Ver
  "Decisões em aberto", item 1.

### `validate` e `list`

- FR14: `validate` (de `010-validate-api-key-cli`), para uma chave válida com permissões,
  imprime uma segunda linha `Permissions: <p1>, <p2>.`, em ordem alfabética. Sem permissões, a
  saída é a de `010`. Exit codes não mudam.
- FR15: `list`, numa instância com catálogo, ganha uma coluna `PERMISSIONS` no fim da tabela,
  com as permissões de cada chave separadas por vírgula, em ordem alfabética. As chaves de FR13
  mostram as permissões padrão, igual ao que o validador devolve. Numa instância sem catálogo a
  coluna não aparece.
- FR16: `list` e `validate` mostram as permissões gravadas mesmo que tenham saído do catálogo
  depois. O catálogo só restringe o que `generate` aceita, não o que já foi emitido.

## Requisitos não-funcionais

- Compatibilidade (versão menor, 1.1.0):
  - Um banco criado pela 1.0.x sobe sem nenhum passo manual. A migration nova só
    acrescenta uma tabela (ver [`data-model.md`](data-model.md)); nenhuma coluna existente
    muda.
  - Numa instância sem catálogo, a saída e os exit codes de `generate`, `revoke`, `list` e
    `validate` são os de antes desta spec (1.0.x, e `010` para `validate`), byte a byte. A suíte caixa-preta de `012` continua verde sem
    nenhuma alteração nos cenários existentes.
  - O uso documentado da biblioteca continua compilando e funcionando:
    `validator.validate(chave)` e `case ApiKeyValidationResult.Valid valid -> valid.clientName()`.
    A exceção é quem desconstrói o record com padrão de record
    (`case Valid(String clientName)`), que deixa de compilar porque o record ganha um
    componente. O README não usa essa forma, e a nota de release da 1.1.0 deve avisar.
- A chave em texto puro continua nunca aparecendo em log nem em mensagem de erro, inclusive
  nos erros novos de `--permissions`.
- Validar uma chave continua sendo uma consulta pelo hash. Buscar as permissões não pode virar
  uma consulta por chave listada em `list` (sem N+1).

## Fora de escopo

- Mudar as permissões de uma chave já emitida (ver FR10).
- Permissões que valem para vários serviços (o exemplo da issue #14 de um aplicativo de
  marketing mudando templates de qualquer serviço). Cada instância só conhece o próprio
  catálogo. Uma chave com acesso a dois serviços continua sendo duas chaves, uma por instância.
- Hierarquia ou curinga de permissões (`email:*`, uma permissão que implica outra).
- Gerenciar o catálogo pela CLI. Ele é configuração da instância, como o pepper.
- Filtrar `list` por permissão (`--permission <nome>`). Pode virar uma spec própria se for
  necessário.
- Tornar a coluna `PERMISSIONS` opcional em `list` numa instância com catálogo.

## Decisões em aberto

Nenhuma — as duas que estavam pendentes foram resolvidas:

1. **Chaves emitidas antes da 1.1.0 (FR13).** Elas não têm permissão gravada e são tratadas
   como se tivessem as permissões padrão do catálogo, na hora da validação. Assim o
   `jogo-acoes` continua funcionando sem gerar chave nova. Consequências aceitas: o serviço
   protegido precisa ler o mesmo catálogo (`api-key.permissions`), como já lê o pepper; e, se
   as permissões padrão mudarem depois, as permissões dessas chaves mudam junto. Como ainda
   não há chave real em produção, o impacto prático é pequeno.

   Alternativa descartada: o validador devolver um conjunto vazio para elas e cada serviço
   tratar o caso à mão. Deixaria a biblioteca sem configuração nova, mas empurraria a regra de
   compatibilidade para cada serviço.
2. **Formato da opção.** Lista separada por vírgula (`--permissions a,b`), que encaixa no
   parser atual (`CliArgs.extractOption` lê uma ocorrência por opção). Alternativa descartada:
   repetir a opção (`--permission a --permission b`), que exigiria mudar o parser.
