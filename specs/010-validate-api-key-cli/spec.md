# Spec: validate-api-key-cli

**Status:** aprovada — não implementada
**Issue:** #<a criar>

## Resumo

Um comando de CLI, `validate`, recebe uma API-KEY em texto puro e diz se ela é válida agora —
e, se for, de qual cliente ela é; se não for, o motivo exato. Usa a mesma regra de validação
que o serviço protegido usa (`ApiKeyValidator`, `008-validate-api-key`), sem reimplementá-la.

## Motivação

Hoje a única forma de confirmar se uma chave funciona é chamar o serviço protegido com ela, ou
consultar o banco calculando o hash à mão. Quem opera a ferramenta precisa, com frequência,
responder "essa chave que o cliente me mandou ainda vale?" — ao dar suporte a um cliente, logo
depois de `generate`, ou para conferir que um `revoke` entrou em vigor. `list`
(`004-list-api-keys`) não resolve: mostra chaves por `id`, nunca pela chave em texto puro (que
não é armazenada).

Reaproveitar `ApiKeyValidator` garante que a resposta da CLI é a mesma que o serviço protegido
daria para a mesma chave no mesmo instante — uma segunda implementação da regra poderia
divergir em silêncio.

## Cenários (comportamento esperado)

Executáveis em `features/validate-api-key-cli.feature` (`api-key-cli`, roda com `mvn verify` — ver
`specs/006-executable-gherkin-scenarios`). Escritos antes do código; em inglês, como as demais
features:

```gherkin
Feature: Validate an API key from the command line

  Scenario: validate a currently active key
    Given client "jogo-acoes" has an active key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0
    And stdout is "API key is valid for client 'jogo-acoes'."

  Scenario: validate an active key that never expires
    Given client "jogo-acoes" has an active key that never expires
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0

  Scenario: validate a key whose revocation is scheduled but not reached yet
    Given client "jogo-acoes" has a key scheduled to be revoked in 14 days
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0

  Scenario: validate with no key on stdin
    When the operator runs "validate" with empty stdin
    Then the exit code is 1
    And stderr is "Error: no API key provided on stdin."

  Scenario: validate a key with the wrong format
    When the operator runs "validate" with "not-a-key" on stdin
    Then the exit code is 2
    And stderr is "Invalid: the key is malformed."

  Scenario: validate a key that was never issued
    When the operator runs "validate" with a well-formed key that was never issued on stdin
    Then the exit code is 3
    And stderr is "Invalid: no API key matches."

  Scenario: validate a revoked key
    Given client "jogo-acoes" has a revoked key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 4
    And stderr is "Invalid: the key was revoked."

  Scenario: validate an expired key
    Given client "jogo-acoes" has an expired key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 5
    And stderr is "Invalid: the key expired."

  Scenario: validate a key that is both expired and revoked
    Given client "jogo-acoes" has a key that is both expired and revoked
    When the operator runs "validate" with that key on stdin
    Then the exit code is 4
```

## Requisitos funcionais

- FR1: Novo comando `validate`, contrato em [`contracts/cli-commands.md`](contracts/cli-commands.md).
- FR2: A chave é lida da **entrada padrão** (primeira linha, sem espaços nas pontas), nunca de
  um argumento de linha de comando. Argumento fica no histórico do shell e aparece em `ps` para
  qualquer usuário da máquina; stdin não. Uso típico: `deployo-api-key validate < chave.txt`, ou
  colar a chave quando o comando espera a entrada.
- FR3: A decisão de validade vem de `ApiKeyValidator.validate` (`api-key-validation`).
  `api-key-cli` passa a depender de `api-key-validation`; nenhuma regra de validação é reescrita
  na CLI.
- FR4: Cada `ApiKeyFailureReason` vira um exit code distinto e uma mensagem fixa (ver
  contrato). `MISSING` é tratado como erro de uso (exit 1), já que significa que nada foi
  informado.
- FR5: Para uma chave válida, a saída mostra só o nome do cliente. Nunca imprime a chave, o
  hash nem o `id` — o `id` não é necessário para responder "vale ou não", e não expor nada além
  do necessário segue o mesmo princípio de `004-list-api-keys`.
- FR6: A chave em texto puro nunca aparece em log nem em mensagem de erro, em nenhum branch —
  mesmo princípio de `001-generate-api-key` e `008-validate-api-key` FR6.
- FR7: O comando é só leitura — não altera nenhuma linha do banco.
- FR8: "Agora" é o instante do `Clock` que a aplicação injeta no `ApiKeyValidator`. Se a spec
  de clock configurável (`011`) for implementada, `validate` passa a respeitar o clock
  configurado sem mudança neste comando.

## Requisitos não-funcionais

- Nenhuma mudança de schema, nenhuma migration nova.
- Os comandos existentes (`generate`, `revoke`, `list`) não mudam de comportamento.
- Exit codes estáveis, para o comando poder ser usado em script
  (`if deployo-api-key validate < chave.txt; then ...`).

## Fora de escopo

- Passar a chave por argumento (`--key <chave>`) ou variável de ambiente — ver FR2.
- Verificar várias chaves de uma vez (uma por linha) — só a primeira linha é considerada.
- Saída em JSON ou outro formato estruturado.
- Mostrar `expires_at`/`revoked_at` da chave verificada — isso já está em `list`.
- Testar a chave contra o serviço protegido de verdade (chamada HTTP) — o comando consulta o
  banco diretamente, pela mesma biblioteca que o serviço usa.

## Decisões em aberto

Nenhuma — as duas que estavam pendentes foram resolvidas:

- Nome do comando: `validate`, alinhado ao nome da biblioteca de `008-validate-api-key`
  (em vez de `check`).
- Chave inválida sai em stderr, igual aos erros dos outros comandos; stdout fica só para a
  chave válida. O exit code continua sendo o que diferencia cada motivo.
