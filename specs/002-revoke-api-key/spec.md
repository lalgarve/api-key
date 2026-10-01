# Spec: revoke-api-key

**Status:** aprovada — implementada (T001-T005 concluídas)
**Issue:** #11

## Resumo

Um comando de CLI revoga uma API-KEY já emitida, imediatamente ou após um prazo de carência
configurável — a partir do momento efetivo da revogação, a chave deixa de ser válida para
autenticar chamadas.

## Motivação

Hoje (feature `001-generate-api-key`) uma chave só deixa de funcionar quando atinge seu
`--validity-days`, se algum foi definido — não existe forma de invalidar uma chave antes
disso. Isso é insuficiente para dois casos reais: uma chave comprometida precisa ser
invalidada imediatamente, e uma rotação de chave (trocar a chave em uso por uma nova) precisa
de uma janela de transição em que a chave antiga ainda funciona enquanto o cliente adota a
nova, sem downtime. Um prazo de carência opcional (`--in-days`) cobre os dois casos com o
mesmo comando: omitido, revoga na hora; informado, agenda a revogação para o futuro.

## Cenários (comportamento esperado)

```gherkin
Scenario: revoke a key immediately
  Given a key with id 3, not yet revoked
  When the operator runs "revoke --id 3"
  Then the key's revocation moment is set to now
  And the key can no longer authenticate from this moment on

Scenario: revoke a key with a grace period
  Given a key with id 3, not yet revoked
  When the operator runs "revoke --id 3 --in-days 14"
  Then the key's revocation moment is set to 14 days from now
  And the key continues to authenticate until that moment

Scenario: --id is required
  Given the operator runs "revoke" without "--id"
  When the command is executed
  Then it fails with a usage-error exit code
  And nothing is changed

Scenario: --id does not match any key
  Given no key exists with id 999
  When the operator runs "revoke --id 999"
  Then it fails with a not-found exit code
  And nothing is changed

Scenario: --in-days is invalid
  Given the operator runs "revoke --id 3 --in-days 0" (or a negative or non-integer value)
  When the command is executed
  Then it fails with a usage-error exit code
  And nothing is changed

Scenario: key is already revoked
  Given a key with id 3 whose revocation moment is already in the past
  When the operator runs "revoke --id 3" (with or without "--in-days")
  Then it fails with an already-revoked exit code
  And nothing is changed

Scenario: key already has a future revocation scheduled
  Given a key with id 3 scheduled to be revoked in 30 days
  When the operator runs "revoke --id 3 --in-days 5"
  Then the key's revocation moment is updated to 5 days from now (the schedule is replaced)

Scenario: key has already expired
  Given a key with id 3 whose expires_at is already in the past
  When the operator runs "revoke --id 3" (with or without "--in-days")
  Then it fails with an already-expired exit code
  And nothing is changed

Scenario: --in-days would schedule the revocation past the key's own expiration
  Given a key with id 3 whose expires_at is 10 days from now, not yet revoked
  When the operator runs "revoke --id 3 --in-days 20"
  Then it fails with a usage-error exit code
  And nothing is changed
```

## Requisitos funcionais

- FR1: O comando `revoke` exige o identificador numérico da chave (`--id`), não a chave em
  texto puro nem o hash — a chave em texto puro é mostrada uma única vez na geração (`001`) e
  não precisa ser redigitada para revogar.
- FR2: O comando aceita um argumento opcional `--in-days <N>` (inteiro positivo). Quando
  omitido, a revogação é imediata (o momento de revogação é o instante da execução).
- FR3: O momento de revogação é armazenado num único campo (`revoked_at`) que pode estar no
  passado (já revogada), no futuro (agendada) ou nulo (nunca revogada nem agendada).
- FR4: Revogar uma chave cujo `revoked_at` já está no passado falha — nada é alterado.
- FR5: Revogar uma chave cujo `revoked_at` já está no futuro (agendamento existente)
  **substitui** o agendamento pelo novo valor calculado.
- FR6: Revogar uma chave não apaga a linha correspondente — só marca o momento de revogação,
  preservando o histórico (quem foi o cliente, quando foi criada, etc.).
- FR7: Revogar uma chave cujo `expires_at` já passou falha — nada é alterado, mesmo que a
  chave nunca tenha sido explicitamente revogada antes. Uma chave expirada não tem mais nada a
  revogar.
- FR8: O momento de revogação calculado (imediato = agora, ou `agora + N dias` via
  `--in-days`) nunca pode ser posterior a `expires_at`, quando definido. Se o cálculo resultar
  num momento posterior, a operação falha antes de persistir qualquer alteração.

## Requisitos não-funcionais

- O comando nunca exige nem aceita a chave em texto puro como entrada — evita reintroduzir o
  segredo em um terminal/log depois do momento único de exibição definido em `001`.

## Fora de escopo

- Descobrir qual `--id` revogar — fica com `specs/004-list-api-keys` (esta feature assume que
  o operador já sabe o id).
- Revogação automática de chaves antigas ao gerar uma nova para o mesmo cliente — fica com
  `specs/003-auto-revoke-on-rotation`.
- Verificar, no momento de uma chamada real, se a chave usada está revogada — fica com a
  futura biblioteca de leitura (mesmo racional do FR11 de `001-generate-api-key`).
- Desfazer uma revogação ("un-revoke") — se a revogação foi um engano, a solução é gerar uma
  chave nova para o cliente.
- Notificar o cliente dono da chave sobre a revogação.

## Decisões em aberto

Nenhuma — confirmado que um cliente pode ter mais de uma chave ativa simultaneamente de
propósito (não só como efeito colateral transitório de uma rotação em andamento). O modelo já
não impunha nenhuma restrição nesse sentido; esta feature (`revoke`, que opera por `--id`, não
por cliente) não muda de comportamento com a confirmação — o impacto real é em `003` (ver
`plan.md`, risco de surpresa mitigado por `--revoke-old-in-days` ser opt-in) e em `004` (a
listagem já mostra múltiplas linhas por cliente sem tratamento especial).
