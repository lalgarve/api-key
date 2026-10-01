# Spec: auto-revoke-on-rotation

**Status:** aprovada — implementada (T001-T006 concluídas)
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

Executáveis em [`features/auto-revoke-on-rotation.feature`](../../src/test/resources/features/auto-revoke-on-rotation.feature)
(roda com `mvn verify`, junto com a suíte JUnit — ver `specs/006-executable-gherkin-scenarios`;
o cenário de atomicidade usa um spy sobre o repositório real para simular a falha de
persistência). As frases dos passos lá são normalizadas para um vocabulário reutilizável entre
as features; o nome e o comportamento esperado de cada cenário não mudam:

- generate for a client with no pre-existing active key
- generate for a client with an existing active key, without --revoke-old-in-days
- generate with an explicit grace period
- generate with an immediate cutover
- generate for a client with multiple pre-existing active keys
- an old key already has an earlier scheduled revocation
- --revoke-old-in-days is invalid (negativo ou não-numérico)
- persisting the old-key revocation fails

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
