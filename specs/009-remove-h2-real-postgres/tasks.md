# Tasks: H2 sai do projeto — Postgres real sempre

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `src/test/resources/application.yml` de `api-key-core`, `api-key-validation` e `api-key-cli`: trocar o `datasource` de H2 pelos valores de Postgres de `application-docker.yml` (`${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/deployo_api_key}`, `deployo_api_key_admin`, `org.postgresql.Driver`) + `flyway.locations: classpath:db/migration` | — | [P] | #27 |
| T002 | Remover `src/test/resources/application-docker.yml` de `api-key-core` e `api-key-validation` (idênticos ao `application.yml` de teste depois do T001). Em `api-key-cli/src/main/resources/application-docker.yml`, tirar `driver-class-name`/`flyway.locations` explícitos e os comentários sobre H2 | T001 | | #27 |
| T003 | `DeployoApiKeyApplicationTests`: tirar as sobrescritas `jdbc:h2`/`org.h2.Driver` e apagar no fim a chave que o teste gerou (pelo `client_name`). `ApiKeyCliRunnerTest.unknownCommandWordDoesNothing`: trocar `jdbc:h2:mem:unused` por um argumento neutro | T001 | [P] | #27 |
| T004 | `api-key-cli/src/main/resources/application-sandbox.yml`: trocar H2 pelos mesmos valores de `application-docker.yml` (`jdbc:postgresql://localhost:5432/deployo_api_key`, `deployo_api_key_admin`, `org.postgresql.Driver`, `db/migration`). Reescrever o comentário do topo: Postgres nativo do ambiente sandbox, provisionado pelo script de setup do ambiente (ver `plan.md`, "Contexto técnico"), e não mais "No Docker/Postgres available here" | — | [P] | #27 |
| T005 | Remover `api-key-core/src/main/resources/db/migration-h2/` | T001, T004 | | #27 |
| T006 | Remover `com.h2database:h2` dos três `pom.xml` (+ o comentário "on H2" em `api-key-validation/pom.xml`) | T001, T003, T004 | | #27 |
| T007 | Atualizar comentários de teste que dizem "H2" (`GenerateCommandTest`, `RevokeCommandTest`, `GenerateCommandRotationAtomicityTest`, `*PersistenceFailureTest`, `ApiKeysMigrationTest`, `ScenarioState`, `CucumberSpringConfiguration`) | T001 | [P] | #27 |
| T008 | `docker compose up -d --wait db` + `mvn clean verify` **duas vezes seguidas**, sem `docker compose down -v` entre as rodadas, sem `SPRING_PROFILES_ACTIVE`. Confirmar as duas verdes (prova de que nenhum teste depende de banco vazio). Adicionar limpeza em qualquer teste que falhar só na segunda | T001, T002, T003, T006 | | #27 |
| T009 | **Verificação no ambiente sandbox (manual, numa sessão da Claude rodando no sandbox).** Ver o roteiro abaixo da tabela | T004, T005, T006, T008 | | #27 |
| T010 | Com `db` **parado**: `mvn test` falha com erro de conexão claro (não `BUILD SUCCESS`, não H2) | T006 | [P] | #27 |
| T011 | Confirmar CI verde na PR (`ci.yml` sem mudança esperada; ver `plan.md`, linha do `SPRING_PROFILES_ACTIVE`) | T008 | | #27 |
| T012 | Documentação: `memory/constitution.md` ("Nomenclatura de ambientes": `sandbox` não é mais "H2 embarcado"; "CI e cobertura de testes": tirar "perfil `sandbox` (H2)"), README (rodar testes exige `docker compose up -d --wait db`), `docs/context/diario.md` | T004 | [P] | #27 |

### Registro da verificação local (2026-10-03, Windows 11)

A porta 5432 dessa máquina estava ocupada pelo Postgres do `jogo-acoes` (`jogo-acoes-db-1`).
Por isso a verificação usou um `postgres:16` temporário na 55432, com as credenciais do
`docker-compose.yml` e `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/deployo_api_key`.
O container foi removido no fim.

- **T008, primeira tentativa:** a 1ª rodada passou e a 2ª falhou em 2 testes de
  `GenerateCommandTest`, que esperavam `findAll()` com exatamente 1 linha (assumiam tabela
  vazia). A tabela tinha 84 linhas deixadas pelos cenários Cucumber da CLI, que commitam e nunca
  apagavam nada; no H2 em memória isso sumia junto com o processo. Correções: `CucumberHooks`
  passa a apagar, depois de cada cenário, as chaves dos clientes que o cenário usou
  (`ScenarioState.qualifiedClients()`), e `GenerateCommandTest` passa a consultar por
  `findByClientName`.
- **T008, depois das correções:** duas rodadas seguidas de `mvn clean verify`, sem limpar as 84
  linhas antigas (prova de que a suíte tolera um banco sujo). As duas deram `BUILD SUCCESS`:
  core 32, validation 42 e cli 103 testes, 0 falhas, 0 pulados. A contagem de linhas ficou em 84
  antes e depois, ou seja, a suíte não deixa mais resíduo.
- **Auditoria inspirada na issue 41 do `jogo-acoes`** (o mesmo tipo de asserção que só vale
  em banco vazio ou zerado): o resto da suíte já filtra por um `client_name` exclusivo do teste
  ou roda com rollback. Sobrou só `RevokeCommandTest.keyNotFound`, que supunha que o id `999999`
  nunca existe; num Postgres persistente a sequência só cresce. Ele passa a usar
  `Long.MAX_VALUE`. O cenário Gherkin com `"999999"` ficou igual: o `Given` dele confere a
  pré-condição e falharia alto em vez de passar pelo motivo errado.
- **T010:** com o container parado, `mvn test` dá `BUILD FAILURE` já em `api-key-core`
  (13 erros), com `Connection to localhost:55432 refused`. Não houve fallback nem skip.
- **T009** (sandbox) e **T011** (CI) ficam pendentes: T009 é manual, no ambiente sandbox, e T011
  depende da PR.

### Registro da verificação no sandbox (T009, 2026-10-03, ambiente sandbox da Claude)

Branch `development` em `6d3dbfc`. PostgreSQL 16 nativo, cluster `16/main` na porta 5432 (o
cluster `16/email`, na 5433, também roda e não foi tocado).

1. **Setup:** o script de setup do ambiente não fica versionado, então o bloco
   `deployo_api_key` de `plan.md` foi rodado à parte (com o mesmo `psql_as_postgres` e o
   `pg_ctlcluster 16 main start`), duas vezes seguidas. As duas terminaram sem erro (exit 0);
   na segunda, role e banco já existiam e nenhum `CREATE` rodou.
2. **Login:** `psql -U deployo_api_key_admin -d deployo_api_key` devolveu
   `deployo_api_key_admin | deployo_api_key`. O dono do banco é `deployo_api_key_admin`.
3. **jogo_acoes:** existe no mesmo cluster (dono `jogo_acoes_admin`) e conecta normalmente.
   Ele estava sem tabelas no schema `public` neste ambiente novo, o que é o estado de um
   ambiente recém-provisionado, não efeito do api-key.
4. **1º `mvn clean verify`** (sem `SPRING_PROFILES_ACTIVE`, banco ainda sem tabelas):
   `BUILD SUCCESS`. core 32, validation 42, cli 103 testes, 0 falhas, 0 erros, `Skipped: 0`,
   "All coverage checks have been met" nos três módulos. O Flyway aplicou V1 e V2 nessa rodada.
5. **2º `mvn clean verify`**, sem limpar o banco: `BUILD SUCCESS` com as mesmas contagens e
   `Skipped: 0`. A tabela `api_keys` tinha 0 linhas antes e depois, ou seja, a suíte não
   deixou resíduo.
6. **CLI no perfil padrão**, com o jar do passo 5 e `API_KEY_HMAC_PEPPER=sandbox-pepper`: o log
   mostra `No active profile set, falling back to 1 default profile: "sandbox"` e
   `jdbc:postgresql://localhost:5432/deployo_api_key`. O Flyway disse
   `Schema "public" is up to date` porque V1 e V2 já tinham sido aplicadas pela suíte do
   passo 4 (`flyway_schema_history`: V1 e V2, `success = t`), por isso o `Successfully applied`
   não aparece na CLI. `generate --client sandbox-check` imprimiu a chave (id 255); `list`
   mostrou a chave como `active`; `revoke --id 255` respondeu
   `API key 255 for client 'sandbox-check' has been revoked.`. Depois, `list` sem filtro não
   mostra mais a chave (o padrão é `--status active`) e `list --status revoked` mostra a
   chave 255 como `revoked`.
7. **`./data/`:** não existe, nem antes nem depois dos passos 4 a 6.

Nenhum passo falhou e nenhum erro de permissão do `deployo_api_key_admin` apareceu.

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
   `API_KEY_HMAC_PEPPER=sandbox-pepper java -jar api-key-cli/target/api-key-*.jar generate --client sandbox-check`.
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
