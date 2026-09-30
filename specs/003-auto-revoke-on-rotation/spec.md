# Spec: auto-revoke-on-rotation

**Status:** aprovada
**Issue:** #12

## Resumo

Ao gerar uma nova chave para um cliente que já tem alguma chave ativa, o operador pode pedir,
no mesmo comando (`--revoke-old-in-days <N>`), que a(s) chave(s) antiga(s) desse cliente sejam
agendadas automaticamente para revogação depois de um prazo de carência — sem exigir uma
chamada manual separada a `revoke` (`002-revoke-api-key`). Sem esse argumento, `generate`
funciona exatamente como em `001`: gera a chave nova e não toca em nenhuma chave existente.

## Motivação

Uma rotação de chave deliberada (trocar a chave em uso por uma nova, com uma janela de
transição) hoje exige dois comandos separados: gerar a nova e revogar a antiga manualmente com
`revoke` — sujeito a esquecer o segundo passo, deixando a chave antiga válida indefinidamente.
Juntar os dois passos num só comando (`generate --revoke-old-in-days N`) cobre esse caso sem
exigir que o operador se lembre de nada depois. Isso só deve acontecer quando pedido: `generate`
sem esse argumento é usado também para o caso comum de simplesmente adicionar uma chave nova
para um cliente (ex.: múltiplos consumidores do mesmo cliente, chave adicional antes de
desativar a antiga manualmente mais tarde) — nesse caso nenhuma chave existente deveria ser
tocada só porque o cliente já tinha uma.

## Cenários (comportamento esperado)

```gherkin
Scenario: generate for a client with no pre-existing active key
  Given client "jogo-acoes" has no key at all, or only keys already revoked
  When the operator runs "generate --client jogo-acoes"
  Then the new key is created as usual
  And no other row is changed

Scenario: generate for a client with an existing active key, without --revoke-old-in-days
  Given client "jogo-acoes" has one active key (id 3), not revoked nor scheduled
  When the operator runs "generate --client jogo-acoes" (no "--revoke-old-in-days")
  Then the new key is created
  And key id 3 is left untouched — same outcome as if it didn't exist

Scenario: generate with an explicit grace period
  Given client "jogo-acoes" has one active key (id 3)
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 3"
  Then key id 3 has its revocation scheduled for 3 days from now

Scenario: generate with an immediate cutover
  Given client "jogo-acoes" has one active key (id 3)
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 0"
  Then key id 3 is revoked immediately (revocation moment set to now)

Scenario: generate for a client with multiple pre-existing active keys
  Given client "jogo-acoes" has active keys with id 3 and id 5
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 7"
  Then both key id 3 and key id 5 have their revocation scheduled for 7 days from now

Scenario: an old key already has an earlier scheduled revocation
  Given client "jogo-acoes" has an active key (id 3) already scheduled to be revoked in 2 days
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 7"
  Then key id 3's revocation stays scheduled for 2 days from now (not pushed back to 7)

Scenario: --revoke-old-in-days is invalid
  Given the operator runs "generate --client jogo-acoes --revoke-old-in-days -1" (or a non-integer value)
  When the command is executed
  Then it fails with a usage-error exit code
  And no key is generated, and no existing key is changed

Scenario: persisting the old-key revocation fails
  Given client "jogo-acoes" has one active key (id 3)
  When the operator runs "generate --client jogo-acoes" and the database rejects the update to key id 3
  Then the new key is not persisted either — the whole operation fails as a unit
```

## Requisitos funcionais

- FR1: A rotina de auto-revogação só roda quando o operador informa `--revoke-old-in-days`
  explicitamente. Sem esse argumento, `generate` se comporta exatamente como em `001` —
  nenhuma chave existente é lida ou alterada, não há valor padrão que entre em ação sozinho.
- FR2: Quando `--revoke-old-in-days` é informado e a chave nova é gerada com sucesso, o
  sistema busca todas as chaves atualmente ativas do mesmo `client_name` (ver definição de
  "ativa" em `002-revoke-api-key`: `revoked_at` nulo ou no futuro), excluindo a chave
  recém-criada.
- FR3: Cada chave ativa encontrada tem seu `revoked_at` definido para `agora + N dias`, onde
  `N` é o valor informado em `--revoke-old-in-days`.
- FR4: `--revoke-old-in-days`, quando informado, aceita `0` (revogação imediata) ou um inteiro
  positivo; qualquer outro valor (negativo, não numérico) é erro de uso — nenhuma chave é
  gerada nem alterada.
- FR5: Se uma chave antiga já tem `revoked_at` no futuro e esse valor é **anterior** ao novo
  `agora + N` calculado, o valor existente é preservado — a rotina nunca adia uma revogação já
  mais próxima.
- FR6: A chave recém-gerada nunca é afetada por esta rotina — só chaves pré-existentes do
  mesmo cliente podem ser agendadas para revogação.
- FR7: Quando acionada, a geração da chave nova e o agendamento de revogação das chaves
  antigas do mesmo cliente são uma única operação atômica — se qualquer parte falhar, nada é
  persistido.

## Requisitos não-funcionais

- Nenhuma chave de outro cliente é lida ou alterada por esta rotina — o filtro por
  `client_name` é exato.

## Fora de escopo

- Notificar o cliente dono da chave sobre a rotação.
- Revogar chaves de outros clientes.
- Desfazer uma rotação automática — mesma limitação de `002-revoke-api-key` (sem comando
  "un-revoke"; a correção é gerar uma chave nova).
- Qualquer alteração ao comando `revoke` em si — esta feature só muda o comportamento de
  `generate`.

## Decisões em aberto

Nenhuma nesta versão — `--revoke-old-in-days` ser opcional e sem valor padrão resolve, ao
mesmo tempo, a pergunta de qual seria a carência padrão (não existe nenhuma) e a de como
desabilitar a rotina (basta omitir o argumento; é o comportamento padrão de `generate`).
