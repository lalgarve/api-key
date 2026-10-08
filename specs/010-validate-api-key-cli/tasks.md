# Tasks: validate-api-key-cli

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `refactor:` `CliCommand` ganha o método `default execute(args, in, out, err)`, delegando para `execute(args, out, err)`; `ApiKeyCliRunner.run()` passa a chamar sempre a versão de 4 argumentos (com `System.in`); `ApiKeyCliRunnerTest` ajustado pros stubs baterem com a chamada real. Sem mudança de comportamento em `generate`/`revoke`/`list` | — | | #39 |
| T002 | `api-key-cli/pom.xml` ganha a dependência em `api-key-validation` | — | [P] | #39 |
| T003 | `ValidateCommand` (`dev.leilaalgarve.apikey.management`, `api-key-cli`): lê a primeira linha de `in` (trim), despacha pro `ApiKeyValidator`, mapeia cada `ApiKeyFailureReason`/sucesso pro exit code e mensagem de `contracts/cli-commands.md` | T001, T002 | | #39 |
| T004 | Wire `ValidateCommand` em `ApiKeyCliRunner` (construtor + entrada `"validate"` no mapa de comandos) | T003 | | #39 |
| T005 | Testes unitários de `ValidateCommand` cobrindo cada linha do contrato (`ByteArrayInputStream` para a entrada, `ApiKeyValidator` real com `Clock.fixed`, nunca mock da regra de validação) | T003 | | #39 |
| T006 | `features/validate-api-key-cli.feature` (cenários de `spec.md`) + `ScenarioState`/`CommandDispatchSteps` estendidos para fornecer stdin por cenário e despachar via `execute(args, in, out, err)` | T004 | | #39 |
| T007 | Remover a tag `@pending` de `blackbox-tests/features/validate.feature` (`012-blackbox-cli-tests`) e confirmar os cenários verdes contra o jar empacotado | T004 | [P] | #39 |
| T008 | Rodar `mvn clean verify` e confirmar 100% verde (JUnit + Cucumber) em todos os módulos, mesma cobertura mínima de 80%; atualizar a seção "Uso" do `README.md` com o comando `validate` | T005, T006 | | #39 |
| T009 | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T007, T008 | [P] | #39 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
