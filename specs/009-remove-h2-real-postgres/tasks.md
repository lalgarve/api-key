# Tasks: H2 sai do projeto — Postgres real sempre

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `src/test/resources/application.yml` de `api-key-core`, `api-key-validation` e `api-key-cli`: trocar o `datasource` de H2 pelos valores de Postgres de `application-docker.yml` (`${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/deployo_api_key}`, `deployo_api_key_admin`, `org.postgresql.Driver`) + `flyway.locations: classpath:db/migration` | — | [P] | #<n> |
| T002 | Remover `src/test/resources/application-docker.yml` de `api-key-core` e `api-key-validation` (idênticos ao `application.yml` de teste depois do T001). Em `api-key-cli/src/main/resources/application-docker.yml`, tirar `driver-class-name`/`flyway.locations` explícitos e os comentários sobre H2 | T001 | | #<n> |
| T003 | `DeployoApiKeyApplicationTests`: tirar as sobrescritas `jdbc:h2`/`org.h2.Driver` e apagar no fim a chave que o teste gerou (pelo `client_name`). `ApiKeyCliRunnerTest.unknownCommandWordDoesNothing`: trocar `jdbc:h2:mem:unused` por um argumento neutro | T001 | [P] | #<n> |
| T004 | **Bloqueada pela decisão em aberto do `sandbox`** (ver `plan.md`). `api-key-cli/src/main/resources/application-sandbox.yml`: trocar H2 por Postgres real (`db/migration`) apontando para o Postgres escolhido, e reescrever o comentário do topo (não é mais "No Docker/Postgres available here") | decisão do `sandbox` | [P] | #<n> |
| T005 | Remover `api-key-core/src/main/resources/db/migration-h2/` | T001, T004 | | #<n> |
| T006 | Remover `com.h2database:h2` dos três `pom.xml` (+ o comentário "on H2" em `api-key-validation/pom.xml`) | T001, T003, T004 | | #<n> |
| T007 | Atualizar comentários de teste que dizem "H2" (`GenerateCommandTest`, `RevokeCommandTest`, `GenerateCommandRotationAtomicityTest`, `*PersistenceFailureTest`, `ApiKeysMigrationTest`, `ScenarioState`, `CucumberSpringConfiguration`) | T001 | [P] | #<n> |
| T008 | `docker compose up -d --wait db` + `mvn clean verify` **duas vezes seguidas**, sem `docker compose down -v` entre as rodadas, sem `SPRING_PROFILES_ACTIVE`. Confirmar as duas verdes (prova de que nenhum teste depende de banco vazio). Adicionar limpeza em qualquer teste que falhar só na segunda | T001, T002, T003, T006 | | #<n> |
| T009 | Com o Postgres do `sandbox` de pé: `mvn -pl api-key-cli spring-boot:run` (perfil padrão) com um `generate` e confirmar que o Flyway aplica `db/migration` sem erro | T004, T005 | | #<n> |
| T010 | Com `db` **parado**: `mvn test` falha com erro de conexão claro (não `BUILD SUCCESS`, não H2) | T006 | [P] | #<n> |
| T011 | Confirmar CI verde na PR (`ci.yml` sem mudança esperada; ver `plan.md`, linha do `SPRING_PROFILES_ACTIVE`) | T008 | | #<n> |
| T012 | Documentação: `memory/constitution.md` ("Nomenclatura de ambientes": `sandbox` não é mais "H2 embarcado"; "CI e cobertura de testes": tirar "perfil `sandbox` (H2)"), README (rodar testes exige `docker compose up -d --wait db`), `docs/context/diario.md` | T004 | [P] | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada. Label de tipo: `test`/`chore`, conforme o caso.
- T008, T010 e T011 dependem de Docker (ou Postgres) disponível no ambiente de implementação.
  Se não estiver disponível (ex.: sessão da Claude sem Docker), registrar explicitamente o que
  não pôde ser verificado. Não é motivo para reintroduzir H2 como substituto (ver `spec.md`).
- T009 depende do Postgres escolhido para o `sandbox`. Se for nativo do ambiente sandbox, só é
  verificável rodando dentro desse ambiente, não na máquina Windows local.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
