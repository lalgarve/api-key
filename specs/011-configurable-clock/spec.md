# Spec: configurable-clock

**Status:** rascunho
**Issue:** #<a criar>

## Resumo

Duas opções globais da CLI, aceitas por qualquer comando, que mudam o "agora" usado pela
execução: `--clock-start <data>` faz o relógio começar numa data/instante informado, e
`--clock-offset-days <N>` desloca o relógio real em N dias, para frente (N positivo) ou para
trás (N negativo). Sem nenhuma das duas, a CLI continua usando o relógio real do sistema (UTC),
exatamente como hoje.

## Motivação

Boa parte do comportamento do projeto depende do tempo: expiração (`--validity-days` em
`001-generate-api-key`), revogação agendada (`--in-days` em `002-revoke-api-key`), revogação
automática na rotação (`003-auto-revoke-on-rotation`), o status derivado e o filtro
`--revoking-within-days` de `004-list-api-keys`, e a validação (`008-validate-api-key`).
Hoje, para ver uma chave passar de `active` para `expired` num teste manual (por exemplo, com o
Adminer de `007-adminer-manual-testing`), é preciso esperar dias de verdade ou editar
`expires_at`/`revoked_at` direto no banco, o que testa o banco editado à mão e não o fluxo real.

Com o relógio configurável, o teste manual vira uma sequência de comandos: gerar uma chave com
`--validity-days 30` e depois listar com `--clock-offset-days 31` para vê-la `expired`; ou
gerar "no passado" com `--clock-start 2026-01-01` e conferir o efeito hoje. Os testes
automatizados já fixam o instante com um `java.time.Clock` injetado (`008-validate-api-key`,
FR4); esta feature leva a mesma ideia para a CLI, onde ainda há `Instant.now()` direto.

## Cenários (comportamento esperado)

Executáveis num `features/configurable-clock.feature` novo em `api-key-cli`
(ver `specs/006-executable-gherkin-scenarios`).

```gherkin
Scenario: no clock option uses the real system clock
  Given no clock option is passed
  When I run "generate --client acme --validity-days 30"
  Then the key's created_at is the current real instant
  And no clock warning is printed

Scenario: list with a positive offset sees a key as expired
  Given a key for "acme" generated with "--validity-days 30" using the real clock
  When I run "list --status all --clock-offset-days 31"
  Then the key is listed with STATUS "expired"

Scenario: list with a negative offset sees a revoked key as still active
  Given a key for "acme" revoked now using the real clock
  When I run "list --status all --clock-offset-days -1"
  Then the key is listed with STATUS "active"

Scenario: generate with a start date records that date
  When I run "generate --client acme --validity-days 10 --clock-start 2026-01-01"
  Then the key's created_at is 2026-01-01T00:00:00Z (plus the time the command took to run)
  And its expires_at is 10 days after created_at

Scenario: start date accepts a full UTC instant
  When I run "generate --client acme --clock-start 2026-01-01T15:30:00Z"
  Then the key's created_at is 2026-01-01T15:30:00Z (plus the time the command took to run)

Scenario: revoke --in-days is scheduled from the configured clock
  Given an active key for "acme" with id 1
  When I run "revoke --id 1 --in-days 5 --clock-offset-days 10"
  Then the key's revoked_at is 15 days after the real current instant

Scenario: zero offset is the same as no offset
  When I run "list --clock-offset-days 0"
  Then the command behaves as with the real clock

Scenario: both clock options together are rejected
  When I run "list --clock-start 2026-01-01 --clock-offset-days 3"
  Then the exit code is 1
  And stderr is "Error: --clock-start and --clock-offset-days cannot be used together."

Scenario: an invalid start date is rejected
  When I run "list --clock-start 2026-13-01"
  Then the exit code is 1
  And stderr is "Error: --clock-start must be an ISO-8601 date (2026-01-31) or UTC instant (2026-01-31T10:00:00Z)."

Scenario: a non-integer offset is rejected
  When I run "list --clock-offset-days abc"
  Then the exit code is 1
  And stderr is "Error: --clock-offset-days must be an integer (negative, zero or positive)."

Scenario: clock options are refused in staging
  Given the active Spring profile is "staging"
  When I run "list --clock-offset-days 31"
  Then the exit code is 1
  And stderr is "Error: clock options are not allowed in the staging or production environment."
  And the database is not read or written

Scenario: clock options are refused in production
  Given the active Spring profile is "production"
  When I run "generate --client acme --clock-start 2026-01-01"
  Then the exit code is 1
  And stderr is "Error: clock options are not allowed in the staging or production environment."
  And no key is generated

Scenario: a simulated clock is always announced
  When I run "list --clock-offset-days 31"
  Then stderr contains "Warning: simulated clock in use; now is"
  And stdout contains only the command's normal output
```

## Requisitos funcionais

- FR1: Toda leitura de "agora" na CLI vem de um único `java.time.Clock`, em UTC, criado no
  início da execução. Nenhum comando nem política (`GenerateCommand`, `RevokeCommand`,
  `ListCommand`, `OldKeyRotationPolicy`, e o `validate` de `010-validate-api-key-cli`) chama
  `Instant.now()` direto. O mesmo `Clock` é o que o `ApiKeyValidator` de `008-validate-api-key`
  recebe quando roda dentro da CLI.
- FR2: `--clock-start <data>` faz o relógio começar no instante informado e avançar em tempo
  real a partir dele (relógio deslocado, não congelado), de modo que dois instantes lidos na
  mesma execução continuam em ordem. Formatos aceitos:
  - data ISO-8601 (`2026-01-31`), interpretada como meia-noite UTC desse dia;
  - instante ISO-8601 em UTC (`2026-01-31T10:00:00Z`).
  A data pode estar no passado ou no futuro.
- FR3: `--clock-offset-days <N>` desloca o relógio real em N dias de 24 horas. N é um inteiro
  com sinal: positivo vai para o futuro, negativo vai para o passado, zero equivale a não
  passar a opção.
- FR4: As duas opções são globais: aceitas por qualquer comando (`generate`, `revoke`, `list`,
  `validate`), em qualquer posição depois da palavra do comando, e nunca obrigatórias.
- FR5: Passar as duas opções na mesma execução é erro de uso (exit code 1), checado logo depois da checagem de ambiente de FR8, antes de
  qualquer outra validação do comando e antes de qualquer leitura ou escrita no banco.
- FR6: Valor inválido em qualquer das duas opções é erro de uso (exit code 1), com as
  mensagens dos cenários acima, também antes de qualquer acesso ao banco.
- FR7: Quando um relógio simulado está em uso (qualquer das duas opções, exceto
  `--clock-offset-days 0`), a CLI imprime em stderr, antes da saída do comando,
  `Warning: simulated clock in use; now is <instante UTC>.` A saída em stdout continua
  idêntica ao contrato de cada comando, para não quebrar quem lê stdout.
- FR8: Quando o perfil Spring ativo é `staging` ou `production` (nomes de
  `memory/constitution.md`, "Nomenclatura de ambientes"), qualquer das duas opções de relógio,
  inclusive `--clock-offset-days 0`, é recusada com exit code 1 e a mensagem
  `Error: clock options are not allowed in the staging or production environment.` A recusa
  acontece antes de qualquer outra validação, inclusive a de FR5/FR6, e antes de qualquer
  acesso ao banco. Nos perfis `sandbox` e `docker` as opções são aceitas.
- FR9: O relógio só é configurado pelas opções de linha de comando. Nenhuma variável de
  ambiente nem propriedade em `application*.yml` muda o relógio, para que um relógio simulado
  apareça sempre no próprio comando.
- FR10: Datas gravadas sob relógio simulado (`created_at`, `expires_at`, `revoked_at`) são
  gravadas como vieram do relógio simulado, sem nenhuma marca no banco. O sistema está em
  pré-produção (ver `memory/constitution.md`), então não há dado real a proteger.
- FR11: A biblioteca `api-key-validation` não muda: ela já recebe o `Clock` do serviço que a
  consome. Esta feature não cria nenhuma opção de relógio para o serviço protegido.

## Requisitos não-funcionais

- Nenhuma mudança de schema nem migration Flyway.
- Sem as opções de relógio, a saída e os exit codes de todos os comandos ficam idênticos aos
  de hoje, e a suíte atual continua passando sem alteração de comportamento.
- O cálculo de "agora" continua com custo constante por leitura.

## Fora de escopo

- Relógio congelado (um instante fixo que não avança durante a execução).
- Configurar o relógio por variável de ambiente ou por `application*.yml` (ver FR9).
- Unidades menores que um dia em `--clock-offset-days` (horas, minutos) ou fusos diferentes de
  UTC em `--clock-start`.
- Qualquer relógio configurável dentro de `api-key-validation` ou no serviço protegido.
- Criar os perfis `staging` e `production`: eles ainda não existem; FR8 só define o que
  acontece quando um deles estiver ativo.

## Decisões em aberto

Nenhuma. As duas que estavam pendentes foram resolvidas pela Leila em 2026-10-03: sem variável
de ambiente (FR9) e opções recusadas em `staging` e `production` (FR8).
