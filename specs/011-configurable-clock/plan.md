# Plan: configurable-clock

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

A CLI (`api-key-cli`) é um processo de execução curta: o Spring Boot sobe o contexto,
`ApiKeyCliRunner` escolhe o comando por `args[0]` e chama `CliCommand.execute(args, out, err)`.
Hoje quatro pontos leem o tempo com `Instant.now()` direto: `GenerateCommand` (linha do
`createdAt`), `RevokeCommand`, `ListCommand` e `OldKeyRotationPolicy`. O `ApiKeyValidator` de
`api-key-validation` já recebe um `java.time.Clock` (bean do consumidor via `ObjectProvider`,
com `Clock.systemUTC()` como fallback), e o `validate` de `010-validate-api-key-cli` o usa dentro da
CLI.

Os cenários Cucumber de `api-key-cli` chamam os comandos direto
(`CommandDispatchSteps`), sem passar pelo `ApiKeyCliRunner`. Os testes rodam sempre contra
PostgreSQL real (`009-remove-h2-real-postgres`).

Os perfis `staging` e `production` ainda não existem no projeto; os ativos hoje são `sandbox`
(padrão, via `spring.profiles.default`) e `docker`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como o relógio chega aos comandos? | Um bean `CliClock` (subclasse de `java.time.Clock`, em UTC) que delega para um `Clock` interno. Começa delegando para `Clock.systemUTC()`; o `ApiKeyCliRunner` troca o delegado depois de validar as opções e antes de chamar o comando. Comandos e `OldKeyRotationPolicy` recebem `Clock` no construtor | resolvida | Alternativas: (a) passar o `Clock` como parâmetro de `CliCommand.execute` muda a assinatura de todos os comandos e obriga o `validate` da 010 a construir um `ApiKeyValidator` por chamada, já que hoje o validador recebe o `Clock` uma vez, como bean; (b) criar um `@Bean Clock` imutável a partir de `ApplicationArguments` no startup não deixa os testes (que compartilham um contexto Spring sem argumentos) exercitar as opções, e um valor inválido viraria falha de criação de bean em vez de exit code 1 com mensagem. O delegado mutável é aceitável porque a CLI executa um comando por processo; os testes restauram o relógio real depois de cada cenário |
| Relógio deslocado ou congelado? | Deslocado: `Clock.offset(Clock.systemUTC(), delta)`. Para `--clock-start`, `delta = start - Clock.systemUTC().instant()`, calculado uma vez ao configurar; para `--clock-offset-days N`, `delta = Duration.ofDays(N)` | resolvida | Spec FR2/FR3: o relógio continua andando, então dois instantes lidos na mesma execução ficam em ordem (ex.: `created_at` e o `revoked_at` da rotação em `generate`) |
| Onde as opções são lidas e validadas? | Uma classe `ClockOptions` (package `dev.leilaalgarve.apikey`) com `parse(String[] args)` que devolve um resultado tipado: nenhuma opção, relógio simulado (com o `delta`), ou erro com a mensagem exata do contrato. Usa `CliArgs.extractOption`, como os comandos | resolvida | Mantém a regra de parsing num lugar só, testável sem Spring. Os comandos ignoram flags que não conhecem (`CliArgs.extractOption` só procura a flag pedida), então não precisam saber das opções de relógio |
| Ordem das checagens no runner | 1) perfil `staging`/`production` ativo e alguma opção de relógio presente → erro; 2) `ClockOptions.parse` com erro → erro; 3) relógio simulado → troca o delegado do `CliClock` e imprime o aviso em stderr; 4) chama o comando. Tudo antes do comando, portanto antes de qualquer acesso ao banco | resolvida | Spec FR5, FR6, FR8: a checagem de ambiente vem primeiro e vale até para `--clock-offset-days 0`; nenhum erro de relógio pode deixar escrita parcial |
| Como detectar `staging`/`production`? | `Environment.acceptsProfiles(Profiles.of("staging", "production"))`, injetando `Environment` no runner | resolvida | Usa os nomes de perfil da constitution ("Nomenclatura de ambientes"). Funciona antes desses perfis existirem: basta um deles estar ativo |
| Como os cenários Cucumber exercitam as opções? | `ApiKeyCliRunner` ganha um método `int dispatch(String[] args, PrintStream out, PrintStream err)` que faz as checagens e chama o comando; `run(...)` passa a ser só `dispatch` com `System.out`/`System.err` mais o `ProcessExiter`. Os passos de `configurable-clock.feature` chamam `dispatch`. Para os cenários de `staging`/`production`, o passo cria um `ApiKeyCliRunner` com um `MockEnvironment` com o perfil ativo, reaproveitando os mesmos beans de comando | resolvida | Os cenários atuais chamam os comandos direto e continuariam sem ver as opções de relógio. Os cenários existentes não mudam |
| `--clock-offset-days 0` | Aceito, sem aviso, mas recusado em `staging`/`production` | resolvida | Spec FR3, FR7 e FR8 |
| Variável de ambiente ou propriedade para o relógio | Nenhuma | resolvida | Decisão da Leila em 2026-10-03 (spec FR9) |

## Estrutura de módulos/pacotes

Só `api-key-cli` muda. `api-key-core` e `api-key-validation` ficam como estão (spec FR11).

| Classe | Mudança |
|---|---|
| `dev.leilaalgarve.apikey.CliClock` | Nova. `@Component`, estende `Clock`, zona UTC, `instant()` delega; `simulate(Duration delta)` troca o delegado para `Clock.offset(Clock.systemUTC(), delta)`; `reset()` volta para `Clock.systemUTC()` (usado pelos testes) |
| `dev.leilaalgarve.apikey.ClockOptions` | Nova. Parsing e validação de `--clock-start`/`--clock-offset-days`, mensagens de erro de `contracts/cli-options.md` |
| `dev.leilaalgarve.apikey.ApiKeyCliRunner` | Recebe `CliClock` e `Environment`; ganha `dispatch(...)` com as checagens na ordem acima |
| `GenerateCommand`, `RevokeCommand`, `ListCommand`, `OldKeyRotationPolicy` | Recebem `Clock` no construtor e trocam `Instant.now()` por `clock.instant()`, lido uma vez por execução |
| `validate` (`010-validate-api-key-cli`) | Nenhuma mudança: o `ApiKeyValidator` recebe o `CliClock` como bean `Clock` pelo `ObjectProvider` |

Como `CliClock` é o único bean `Clock` do contexto da CLI, o `ObjectProvider<Clock>` do
`ApiKeyValidator` o encontra sem `@Primary`.

## Riscos e trade-offs

- **Estado mutável num singleton.** `CliClock` muda de delegado durante a execução. Na CLI real
  isso acontece uma vez, antes do comando. Nos testes, que compartilham o contexto Spring, um
  cenário que esquecer de restaurar o relógio contaminaria os seguintes; por isso um hook
  `@After` dos cenários Cucumber (e o `@AfterEach` dos testes JUnit que simulam relógio) chama
  `CliClock.reset()`.
- **Spring também lê os argumentos.** O `SimpleCommandLinePropertySource` do Spring Boot trata
  argumentos começando com `--` como propriedades; `--clock-offset-days` seguido de `-1` vira
  uma propriedade sem valor, que nada lê. É o mesmo comportamento das flags que os comandos já
  usam (`--client`, `--in-days`). O teste de `-1` em T008 confirma que o valor negativo chega ao
  `ClockOptions`.
- **Aviso só em stderr.** Quem lê só stdout de um comando com relógio simulado não vê o aviso.
  É o preço de manter o stdout igual ao contrato de cada comando (spec FR7).
- **Perfis inexistentes.** A recusa em `staging`/`production` só é testada com perfil simulado
  (`MockEnvironment`), já que esses perfis ainda não têm `application-*.yml`. Quando forem
  criados, nenhuma mudança aqui é necessária.
- **Datas gravadas com relógio simulado ficam indistinguíveis no banco** (spec FR10). Aceitável
  em pré-produção; com a recusa em `staging`/`production`, isso não chega a dado real.
