# Tasks: key-permissions

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `feat:` migration `V3__create_api_key_permissions_table.sql` e `ApiKey` com `permissions` (`@ElementCollection`, `EAGER` + `SUBSELECT`), construtor novo, o atual mantido. Teste de repositório: grava e relê as permissões; `findAll` de várias chaves não faz uma consulta por chave; apagar a chave apaga as permissões | — | | #14 |
| T002 | `feat:` `PermissionCatalog` e `InvalidPermissionCatalogException` em `api-key-core`, lendo `api-key.permissions` pelo `Binder`. `PermissionCatalogTest` cobre: sem catálogo, lista vazia, nome inválido, nome com mais de 100 caracteres, nome repetido, nenhum padrão, `effectivePermissions` com e sem permissões gravadas | — | [P] | #14 |
| T003 | `feat:` `ApiKeyValidationResult.Valid` com `permissions` e `hasPermission`, construtor de um argumento; `ApiKeyValidator` com os construtores do contrato, devolvendo `effectivePermissions`. Testes das três linhas da tabela do contrato e da exceção com catálogo inválido. `validate-api-key.feature` de `api-key-validation` continua verde sem mudança | T001, T002 | | #14 |
| T004 | `feat:` `ApiKeyCliRunner` chama `catalog.requireValid()` antes de despachar e sai com 2 e a mensagem do contrato. `ApiKeyCliRunnerTest` cobre o ramo novo | T002 | [P] | #14 |
| T005 | `feat:` `CliArgs.parseCommaList` e `--permissions` em `GenerateCommand`: os três erros novos (exit 1, nada gravado nem impresso), padrão sem a opção, gravação na mesma transação, última linha `Permissions: ...`. Testes de `GenerateCommand`, incluindo falha ao gravar com rotação (`003`) | T001, T002 | | #14 |
| T006 | `feat:` coluna `PERMISSIONS` em `ListCommand` quando há catálogo, com as permissões efetivas | T001, T002 | [P] | #14 |
| T007 | `feat:` segunda linha `Permissions: ...` em `ValidateCommand` | T003 | [P] | #14 |
| T008 | `test:` `features/key-permissions.feature` em `api-key-cli` com os cenários de `spec.md`; `ScenarioState` guarda o catálogo do cenário e `CommandDispatchSteps` monta os comandos com ele. `spec.md` passa a apontar para o `.feature` | T004-T007 | | #14 |
| T009 | `test:` `blackbox-tests/features/permissions.feature`, catálogo por variável de ambiente; os `.feature` existentes rodam sem mudança | T004-T007 | [P] | #14 |
| T010 | `docs:` README (uso de `--permissions`, catálogo, coluna nova, segunda linha de `validate`, ordem do upgrade) e `008-validate-api-key/http-integration.md` (catálogo no serviço, `hasPermission`, sugestão de 403) | T003-T007 | [P] | #14 |
| T011 | `chore:` rodar `mvn clean verify` (JUnit + Cucumber, cobertura mínima de 80%) e a suíte caixa-preta; marcar a spec como implementada; registrar no diário | T008-T010 | | #14 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- A versão 1.1.0 nos `pom.xml` e a nota de release ficam com a preparação da release, comum a
  `010` e `013`. A nota precisa citar a quebra do padrão de record e a ordem do upgrade (ver
  `plan.md`, "Riscos e trade-offs").
