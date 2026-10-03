# Tasks: H2 sai do projeto — Postgres real sempre

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `src/test/resources/application.yml` de `api-key-core`, `api-key-validation` e `api-key-cli`: trocar o `datasource` de H2 pelos valores de Postgres de `application-docker.yml` (`${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/deployo_api_key}`, `deployo_api_key_admin`, `org.postgresql.Driver`) + `flyway.locations: classpath:db/migration` | — | [P] | #<n> |
| T002 | Remover `src/test/resources/application-docker.yml` de `api-key-core` e `api-key-validation` (idênticos ao `application.yml` de teste depois do T001). Em `api-key-cli/src/main/resources/application-docker.yml`, tirar `driver-class-name`/`flyway.locations` explícitos e os comentários sobre H2 | T001 | | #<n> |
| T003 | `DeployoApiKeyApplicationTests`: tirar as sobrescritas `jdbc:h2`/`org.h2.Driver` e apagar no fim a chave que o teste gerou (pelo `client_name`). `ApiKeyCliRunnerTest.unknownCommandWordDoesNothing`: trocar `jdbc:h2:mem:unused` por um argumento neutro | T001 | [P] | #<n> |
| T004 | `api-key-cli/src/main/resources/application-sandbox.yml`: trocar H2 pelos mesmos valores de `application-docker.yml` (`jdbc:postgresql://localhost:5432/deployo_api_key`, `deployo_api_key_admin`, `org.postgresql.Driver`, `db/migration`). Reescrever o comentário do topo: Postgres nativo do ambiente sandbox, provisionado pelo script de setup do ambiente (ver `plan.md`, "Contexto técnico"), e não mais "No Docker/Postgres available here" | — | [P] | #<n> |
| T005 | Remover `api-key-core/src/main/resources/db/migration-h2/` | T001, T004 | | #<n> |
| T006 | Remover `com.h2database:h2` dos três `pom.xml` (+ o comentário "on H2" em `api-key-validation/pom.xml`) | T001, T003, T004 | | #<n> |
| T007 | Atualizar comentários de teste que dizem "H2" (`GenerateCommandTest`, `RevokeCommandTest`, `GenerateCommandRotationAtomicityTest`, `*PersistenceFailureTest`, `ApiKeysMigrationTest`, `ScenarioState`, `CucumberSpringConfiguration`) | T001 | [P] | #<n> |
| T008 | `docker compose up -d --wait db` + `mvn clean verify` **duas vezes seguidas**, sem `docker compose down -v` entre as rodadas, sem `SPRING_PROFILES_ACTIVE`. Confirmar as duas verdes (prova de que nenhum teste depende de banco vazio). Adicionar limpeza em qualquer teste que falhar só na segunda | T001, T002, T003, T006 | | #<n> |
| T009 | **Verificação no ambiente sandbox (manual, numa sessão da Claude rodando no sandbox).** Ver o roteiro abaixo da tabela | T004, T005, T006, T008 | | #<n> |
| T010 | Com `db` **parado**: `mvn test` falha com erro de conexão claro (não `BUILD SUCCESS`, não H2) | T006 | [P] | #<n> |
| T011 | Confirmar CI verde na PR (`ci.yml` sem mudança esperada; ver `plan.md`, linha do `SPRING_PROFILES_ACTIVE`) | T008 | | #<n> |
| T012 | Documentação: `memory/constitution.md` ("Nomenclatura de ambientes": `sandbox` não é mais "H2 embarcado"; "CI e cobertura de testes": tirar "perfil `sandbox` (H2)"), README (rodar testes exige `docker compose up -d --wait db`), `docs/context/diario.md` | T004 | [P] | #<n> |

### T009 — roteiro da verificação no sandbox

Feita pela pessoa responsável numa sessão (chat) da Claude rodando no ambiente sandbox, a
partir do branch com T001-T008 já aplicados. O sandbox não tem Docker, então esta é a única
forma de provar o perfil `sandbox` e a suíte contra o Postgres nativo.

1. Rodar o script de setup do ambiente (com o bloco `deployo_api_key` de `plan.md`, "Contexto
   técnico") e confirmar que ele termina sem erro. Rodar **uma segunda vez** e confirmar que
   continua sem erro (idempotência).
2. Confirmar que o banco existe e aceita login com as credenciais do projeto:
   `PGPASSWORD=deployo_api_key_admin psql -h localhost -p 5432 -U deployo_api_key_admin -d deployo_api_key -c 'select current_user, current_database()'`.
3. Confirmar que o `jogo_acoes` continua intacto no mesmo cluster (`psql ... -d jogo_acoes`
   conecta normalmente), já que os dois projetos dividem o `16/main`.
4. `mvn clean verify` na raiz, sem `SPRING_PROFILES_ACTIVE`. Esperado: `BUILD SUCCESS` nos três
   módulos, `Skipped: 0`, cobertura acima do piso.
5. Repetir `mvn clean verify` uma segunda vez, sem limpar o banco. Esperado: verde de novo
   (mesma prova de repetibilidade do T008, agora no Postgres nativo).
6. Rodar a CLI no perfil padrão (`sandbox`) a partir do jar gerado pelo passo 4/5, ex.:
   `API_KEY_HMAC_PEPPER=sandbox-pepper java -jar api-key-cli/target/api-key-cli-*.jar generate --client sandbox-check`.
   Esperado: o Flyway aplica V1 e V2 de `db/migration` (log `Successfully applied` na primeira
   vez, `Schema ... is up to date` nas seguintes), e a chave é impressa. Depois, `list` mostra a
   chave e `revoke --id <id>` a revoga.
7. Confirmar que nada foi criado em `./data/` (o arquivo H2 `./data/deployo_api_key` não deve
   mais aparecer).
8. Registrar o resultado nesta seção: data, saída resumida de cada passo, e qualquer passo
   que falhou ou não pôde ser feito, com o motivo. Se algum passo falhar por permissão do
   `deployo_api_key_admin` (ver a ressalva sobre superusuário em `plan.md`), anotar o comando
   e o erro exatos.

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada. Label de tipo: `test`/`chore`, conforme o caso.
- T008, T010 e T011 dependem de Docker (ou Postgres) disponível no ambiente de implementação.
  Se não estiver disponível (ex.: sessão da Claude sem Docker), registrar explicitamente o que
  não pôde ser verificado. Não é motivo para reintroduzir H2 como substituto (ver `spec.md`).
- T009 só é verificável dentro do ambiente sandbox da Claude, não na máquina Windows local.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
