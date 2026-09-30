# Spec: auto-revoke-on-rotation

**Status:** rascunho
**Issue:** #12

## Resumo

Ao gerar uma nova chave para um cliente que já tem alguma chave ativa, a(s) chave(s) antiga(s)
desse cliente são agendadas automaticamente para revogação depois de um prazo de carência —
por padrão, ou informado explicitamente — sem exigir uma chamada manual a `revoke`
(`002-revoke-api-key`).

## Motivação

Sem isso, trocar a chave de um cliente é um processo manual de duas etapas: gerar a nova
chave, e lembrar de revogar a antiga separadamente com `revoke`. Esquecer o segundo passo
deixa uma chave antiga válida indefinidamente mesmo depois do cliente já ter adotado a nova —
o oposto do que "rotação de chave" deveria garantir. Automatizar o agendamento da revogação da
chave antiga, com uma carência configurável, cobre o caso comum (trocar de chave sem
downtime: as duas funcionam durante a janela de transição) sem exigir que o operador se lembre
de nada depois de rodar `generate`.

## Cenários (comportamento esperado)

```gherkin
Scenario: generate for a client with no pre-existing active key
  Given client "jogo-acoes" has no key at all, or only keys already revoked
  When the operator runs "generate --client jogo-acoes"
  Then the new key is created as usual
  And no other row is changed

Scenario: generate for a client with one existing active key, using the default grace period
  Given client "jogo-acoes" has one active key (id 3), not revoked nor scheduled
  When the operator runs "generate --client jogo-acoes" without "--revoke-old-in-days"
  Then the new key is created
  And key id 3 has its revocation scheduled for the default grace period from now

Scenario: generate with a custom grace period
  Given client "jogo-acoes" has one active key (id 3)
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 3"
  Then key id 3 has its revocation scheduled for 3 days from now

Scenario: generate with an immediate cutover
  Given client "jogo-acoes" has one active key (id 3)
  When the operator runs "generate --client jogo-acoes --revoke-old-in-days 0"
  Then key id 3 is revoked immediately (revocation moment set to now)

Scenario: generate for a client with multiple pre-existing active keys
  Given client "jogo-acoes" has active keys with id 3 and id 5
  When the operator runs "generate --client jogo-acoes"
  Then both key id 3 and key id 5 have their revocation scheduled for the same grace period

Scenario: an old key already has an earlier scheduled revocation
  Given client "jogo-acoes" has an active key (id 3) already scheduled to be revoked in 2 days
  When the operator runs "generate --client jogo-acoes" with a default/custom grace period of 7 days
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

- FR1: Ao gerar uma chave nova com sucesso, o sistema busca todas as chaves atualmente ativas
  do mesmo `client_name` (ver definição de "ativa" em `002-revoke-api-key`: `revoked_at` nulo
  ou no futuro), excluindo a chave recém-criada.
- FR2: Cada chave ativa encontrada tem seu `revoked_at` definido para `agora + N dias`, onde
  `N` vem de `--revoke-old-in-days` quando informado, ou do valor padrão do sistema quando
  omitido (ver "Decisões em aberto").
- FR3: `--revoke-old-in-days` aceita `0` (revogação imediata) ou um inteiro positivo; qualquer
  outro valor (negativo, não numérico) é erro de uso — nenhuma chave é gerada nem alterada.
- FR4: Se uma chave antiga já tem `revoked_at` no futuro e esse valor é **anterior** ao novo
  `agora + N` calculado, o valor existente é preservado — a rotina nunca adia uma revogação já
  mais próxima.
- FR5: A chave recém-gerada nunca é afetada por esta rotina — só chaves pré-existentes do
  mesmo cliente podem ser agendadas para revogação.
- FR6: A geração da chave nova e o agendamento de revogação das chaves antigas do mesmo
  cliente são uma única operação atômica — se qualquer parte falhar, nada é persistido.
- FR7: Este comportamento roda sempre que `generate` é chamado para um cliente com alguma
  chave ativa — não há hoje uma forma de desabilitá-lo por completo (ver decisão em aberto).

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

- Valor padrão de `N` (dias de carência) quando `--revoke-old-in-days` é omitido — proposta:
  7 dias, a confirmar.
- Deveria existir uma forma de desabilitar completamente a rotação automática (manter uma
  chave antiga ativa para sempre, mesmo gerando uma nova para o mesmo cliente)? Hoje a única
  forma de aproximar isso é informar um `N` muito grande — não há uma flag de opt-out
  explícita. Relevante se a intenção for, em algum momento, permitir múltiplas chaves ativas
  de propósito para o mesmo cliente (mesma decisão em aberto em `002-revoke-api-key`).
- Onde o valor padrão de `N` deveria morar — constante no código, variável de ambiente
  (mesmo padrão do pepper do HMAC em `001`), ou argumento de configuração da aplicação — a
  confirmar junto da definição do valor em si.
