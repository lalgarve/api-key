# Spec: list-api-keys

**Status:** aprovada — implementada (T001-T004 concluídas)
**Issue:** #13

## Resumo

Um comando de CLI lista as API-KEYs já emitidas, com filtros por status (ativa/expirada/
revogada/todas), por cliente, e por proximidade de uma revogação agendada — sem nunca expor o
hash nem a chave em texto puro.

## Motivação

Sem este comando não há como saber, a partir da CLI, quais chaves existem, quais já foram ou
serão revogadas, nem localizar o `id` necessário para usar `revoke`
(`002-revoke-api-key`) — hoje a única forma de responder a essas perguntas é consultar o banco
de dados diretamente, fora do fluxo normal de operação desta ferramenta.

## Cenários (comportamento esperado)

Executáveis em [`features/list-api-keys.feature`](../../src/test/resources/features/list-api-keys.feature)
(roda com `mvn verify`, junto com a suíte JUnit — ver `specs/006-executable-gherkin-scenarios`;
verificados contra a tabela impressa de verdade, não recalculando o status por fora). As
frases dos passos lá são normalizadas para um vocabulário reutilizável entre as features; o
nome e o comportamento esperado de cada cenário não mudam:

- list with no filters shows only active keys
- list all keys
- list only revoked keys
- list only expired keys
- a key that is both expired and revoked shows as revoked
- list filtered by client
- list keys scheduled to be revoked soon
- --revoking-within-days combined with --status revoked is invalid
- --revoking-within-days combined with --status expired is invalid
- no keys match the filters
- --revoking-within-days is invalid (zero, negativo, ou não-numérico)

## Requisitos funcionais

- FR1: Cada chave tem um status derivado, calculado a partir de `revoked_at` e `expires_at`
  (nunca armazenado), nesta ordem de prioridade:
  1. **`revoked`** — `revoked_at` não nulo e já no passado.
  2. **`expired`** — não é `revoked` pela regra acima, e `expires_at` não nulo e já no passado.
  3. **`active`** — qualquer outro caso (inclui `revoked_at` nulo ou ainda no futuro —
     revogação agendada mas ainda não em vigor).
  Como `002-revoke-api-key` garante `revoked_at <= expires_at` quando ambos existem (ver
  `data-model.md` de `002`), uma chave nunca é `expired` e `revoked` ao mesmo tempo — a
  prioridade acima só desempata o caso em que ambos os momentos já passaram.
- FR2: Sem argumentos, `list` mostra só chaves com status `active`.
- FR3: `--status` aceita `active` (padrão explícito), `expired`, `revoked`, ou `all` (sem
  filtro de status).
- FR4: `--client <nome>` filtra por `client_name` exato, combinável com `--status`.
- FR5: `--revoking-within-days <N>` mostra só chaves com status `active` cujo `revoked_at`
  está no futuro e dentro de `N` dias a partir de agora (agendadas para revogação em breve).
  `N` deve ser um inteiro positivo.
- FR6: `--revoking-within-days` só é aceito com `--status` omitido ou `--status active`;
  qualquer outra combinação (`expired`, `revoked` ou `all`) falha com erro de uso, já que só
  uma chave `active` pode ter uma revogação futura agendada.
- FR7: A saída nunca inclui `key_hash` nem a chave em texto puro — só `id`, cliente, data de
  criação, data de expiração (quando houver), informação de revogação (quando houver) e o
  status derivado (FR1).
- FR8: Quando nenhuma chave corresponde aos filtros, o comando termina com sucesso (não é
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

Nenhuma — resolvida pela introdução do status `expired` (FR1): uma chave expirada não
aparece mais como `active`.
