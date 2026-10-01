# Tasks: Tornar os cenários Gherkin executáveis

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Adicionar Cucumber (`cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine`) e `junit-platform-suite` ao `pom.xml` | — | | #18 |
| ~~T002~~ | Infraestrutura: `RunCucumberTest`, `CucumberSpringConfiguration` (contexto Spring + spy do repositório), `CucumberHooks` (`@Before`/`@After`), `ScenarioState` | T001 | | #18 |
| ~~T003~~ | `CommandDispatchSteps`: despacho genérico de `generate`/`revoke`/`list`, tokenização da linha de comando, passos genéricos de exit code/stdout/stderr | T002 | | #18 |
| ~~T004~~ | `KeyLifecycleSteps`: criação/mutação/consulta de chaves (ativa, expirada, revogada, agendada) | T002 | [P] | #18 |
| ~~T005~~ | `features/generate-api-key.feature` — cenários de `specs/001-generate-api-key/spec.md` | T003, T004 | [P] | #18 |
| ~~T006~~ | `features/revoke-api-key.feature` — cenários de `specs/002-revoke-api-key/spec.md` | T003, T004 | [P] | #18 |
| ~~T007~~ | `features/auto-revoke-on-rotation.feature` — cenários de `specs/003-auto-revoke-on-rotation/spec.md`, incluindo o cenário de atomicidade via o spy do repositório | T003, T004 | [P] | #18 |
| ~~T008~~ | `ListingSteps` + `features/list-api-keys.feature` — cenários de `specs/004-list-api-keys/spec.md`, verificados contra a tabela impressa | T003, T004 | [P] | #18 |
| ~~T009~~ | Atualizar a seção "Cenários" de `specs/001` a `specs/004`'s `spec.md` para referenciar o `.feature` correspondente em vez de duplicar a prosa Gherkin inline | T005, T006, T007, T008 | | #18 |
| ~~T010~~ | Rodar `mvn clean verify` e confirmar 100% verde (JUnit + Cucumber), repetindo algumas vezes para descartar instabilidade | T009 | | #18 |
| ~~T011~~ | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T010 | [P] | #18 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Todas as tarefas desta feature estão concluídas.
