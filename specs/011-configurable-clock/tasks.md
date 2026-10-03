# Tasks: configurable-clock

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `refactor:` criar `CliClock` (bean `Clock` UTC delegando para `Clock.systemUTC()`, com `simulate(Duration)` e `reset()`) e injetar `Clock` em `GenerateCommand`, `RevokeCommand`, `ListCommand` e `OldKeyRotationPolicy`, trocando todo `Instant.now()` de `api-key-cli/src/main` por `clock.instant()` lido uma vez por execução. Sem mudança de comportamento: mesma suíte verde | — | | #<n> |
| T002 | Testes unitários de `CliClock`: delega para o relógio real por padrão, `simulate` desloca e continua avançando, `reset` volta ao real | T001 | [P] | #<n> |
| T003 | `ClockOptions.parse(String[])`: nenhuma opção, relógio simulado (`delta` de `--clock-start` data ou instante, ou de `--clock-offset-days` com sinal), ou erro com as mensagens de `contracts/cli-options.md` | — | [P] | #<n> |
| T004 | Testes unitários de `ClockOptions`: data, instante, data inválida (`2026-13-01`), valor ausente, offset positivo/negativo/zero/não inteiro, as duas opções juntas | T003 | | #<n> |
| T005 | `ApiKeyCliRunner.dispatch(args, out, err)`: recusa em `staging`/`production` (via `Environment`), erros de `ClockOptions`, `CliClock.simulate` + aviso em stderr, depois o comando. `run(...)` passa a delegar para `dispatch` | T001, T003 | | #<n> |
| T006 | `ApiKeyCliRunnerTest`: com `MockEnvironment`, recusa em `staging` e em `production` (inclusive `--clock-offset-days 0`) sem chamar o comando; ordem das checagens; aviso só com relógio simulado; comando desconhecido continua no-op | T005 | | #<n> |
| T007 | `features/configurable-clock.feature` com os cenários de `spec.md`, passos que chamam `ApiKeyCliRunner.dispatch` (um runner com `MockEnvironment` para `staging`/`production`), hook `@After` que chama `CliClock.reset()` e apaga as chaves criadas | T005 | | #<n> |
| T008 | Confirmar que `--clock-offset-days -1` chega ao `ClockOptions` mesmo com o Spring lendo `--` como propriedade (risco em `plan.md`), coberto por um cenário de T007 rodando pelo jar real ou pelo `main()` | T007 | | #<n> |
| T009 | Verificar com o `check` de `010-check-api-key-cli` (se já mesclado) que uma chave com `--validity-days 30` é recusada como expirada com `--clock-offset-days 31`, sem mudar o validador | T005 | [P] | #<n> |
| T010 | Documentar as opções de relógio no README, na parte de uso da CLI, com o exemplo de teste manual de expiração | T005 | [P] | #<n> |
| T011 | Rodar `mvn clean verify` e confirmar 100% verde (JUnit + Cucumber) nos três módulos, cobertura mínima de 80% por módulo, e rodar a suíte duas vezes seguidas no mesmo banco sem resíduo | T006, T007, T008 | | #<n> |
| T012 | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T011 | [P] | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- T001 é um `refactor:` próprio, antes de qualquer opção nova, para provar que injetar o
  `Clock` não muda o comportamento atual.
- T009 depende de a spec 010 ter sido mesclada; se ainda não tiver, fica para quando for.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
