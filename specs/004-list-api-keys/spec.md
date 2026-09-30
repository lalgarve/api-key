# Spec: list-api-keys

**Status:** rascunho
**Issue:** #13

## Resumo

Um comando de CLI lista as API-KEYs já emitidas, com filtros por status (ativa/revogada/
todas), por cliente, e por proximidade de uma revogação agendada — sem nunca expor o hash nem
a chave em texto puro.

## Motivação

Sem este comando não há como saber, a partir da CLI, quais chaves existem, quais já foram ou
serão revogadas, nem localizar o `id` necessário para usar `revoke`
(`002-revoke-api-key`) — hoje a única forma de responder a essas perguntas é consultar o banco
de dados diretamente, fora do fluxo normal de operação desta ferramenta.

## Cenários (comportamento esperado)

```gherkin
Scenario: list with no filters shows only active keys
  Given some active keys and some revoked keys exist
  When the operator runs "list"
  Then only the active keys are shown

Scenario: list all keys
  Given some active keys and some revoked keys exist
  When the operator runs "list --status all"
  Then both active and revoked keys are shown

Scenario: list only revoked keys
  Given some active keys and some revoked keys exist
  When the operator runs "list --status revoked"
  Then only the revoked keys are shown

Scenario: list filtered by client
  Given keys exist for clients "jogo-acoes" and "billing"
  When the operator runs "list --client jogo-acoes"
  Then only "jogo-acoes" keys are shown, still subject to the default active-only filter

Scenario: list keys scheduled to be revoked soon
  Given an active key scheduled to be revoked in 10 days, and another in 90 days
  When the operator runs "list --revoking-within-days 30"
  Then only the key scheduled within 30 days is shown

Scenario: --revoking-within-days combined with --status revoked is invalid
  Given the operator runs "list --status revoked --revoking-within-days 30"
  When the command is executed
  Then it fails with a usage-error exit code

Scenario: no keys match the filters
  Given no key matches the given filters
  When the command is executed
  Then it succeeds
  And it prints a message saying no keys were found

Scenario: --revoking-within-days is invalid
  Given the operator runs "list --revoking-within-days 0" (or a negative or non-integer value)
  When the command is executed
  Then it fails with a usage-error exit code
```

## Requisitos funcionais

- FR1: Sem argumentos, `list` mostra só chaves ativas (`revoked_at` nulo ou no futuro — mesma
  definição de "ativa" usada em `002-revoke-api-key`/`003-auto-revoke-on-rotation`).
- FR2: `--status` aceita `active` (padrão explícito), `revoked` (`revoked_at` no passado), ou
  `all` (sem filtro de status).
- FR3: `--client <nome>` filtra por `client_name` exato, combinável com `--status`.
- FR4: `--revoking-within-days <N>` mostra só chaves ativas cujo `revoked_at` está no futuro e
  dentro de `N` dias a partir de agora (agendadas para revogação em breve). `N` deve ser um
  inteiro positivo.
- FR5: `--revoking-within-days` só é aceito com `--status` omitido ou `--status active`;
  qualquer outra combinação (`revoked` ou `all`) falha com erro de uso, já que uma chave já
  revogada ou fora do filtro de "ativa" não tem uma "revogação futura" a mostrar.
- FR6: A saída nunca inclui `key_hash` nem a chave em texto puro — só `id`, cliente, data de
  criação, data de expiração (quando houver) e informação de revogação (quando houver).
- FR7: Quando nenhuma chave corresponde aos filtros, o comando termina com sucesso (não é
  erro) e imprime uma mensagem indicando que a lista está vazia.

## Requisitos não-funcionais

- A listagem é somente leitura — nenhuma linha é alterada por este comando.

## Fora de escopo

- Alterar chaves a partir da listagem — usar `revoke` (`002-revoke-api-key`) separadamente.
- Formatos de saída além de texto tabular simples (ex.: JSON, CSV).
- Paginação — volume pequeno esperado (poucos clientes, ver contexto no README do projeto).
- Ordenação customizável pelo operador (ordem fixa — ver `plan.md`).

## Critérios de aceite

Cobertos pelos cenários acima — os filtros combinam corretamente (status × cliente ×
proximidade de revogação), a combinação inválida falha antes de consultar o banco, uma lista
vazia não é tratada como erro, e nenhuma saída expõe hash ou chave em texto puro.

## Decisões em aberto

- Uma chave já expirada (`expires_at` no passado) mas nunca revogada aparece nesta listagem
  como "active", já que o status aqui reflete só revogação, não expiração — isso pode
  confundir o operador (a chave aparece "ativa" mas já não autentica mais). A confirmar se
  isso é aceitável como está, ou se a listagem deveria derivar um terceiro estado (ex.
  "expired") combinando os dois campos.
