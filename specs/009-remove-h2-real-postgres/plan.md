# Plan: H2 sai do projeto — Postgres real sempre

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- **Onde o H2 aparece hoje:**
  - `src/test/resources/application.yml` de `api-key-core`, `api-key-validation` e
    `api-key-cli`: `jdbc:h2:mem:deployo_api_key;MODE=PostgreSQL` + `db/migration-h2`.
  - `api-key-cli/src/main/resources/application-sandbox.yml`:
    `jdbc:h2:file:./data/deployo_api_key` + `db/migration-h2`. É o perfil padrão
    (`spring.profiles.default: sandbox`).
  - `api-key-core/src/main/resources/db/migration-h2/` (V1, V2), espelhando `db/migration/`.
  - `com.h2database:h2` nos três `pom.xml`.
  - `DeployoApiKeyApplicationTests`: passa `jdbc:h2:mem:deployo_api_key_boot` + `org.h2.Driver`
    para o `main()`, buscando um banco isolado.
  - `ApiKeyCliRunnerTest.unknownCommandWordDoesNothing`: passa `jdbc:h2:mem:unused` como
    argumento. O valor é irrelevante: o teste só verifica que uma palavra desconhecida não chama
    nenhum comando.
  - Vários comentários de teste falam em "H2 database" (`GenerateCommandTest`,
    `RevokeCommandTest`, `ApiKeysMigrationTest`, `ScenarioState`, `CucumberSpringConfiguration`
    etc.).
- **O que já é Postgres:** os `application-docker.yml` (CLI em `main`, core e validation em
  `test`), com `SPRING_DATASOURCE_*` sobrescrevíveis por variável de ambiente. O CI
  (`.github/workflows/ci.yml`) já roda `docker compose up -d --wait db` + `mvn -B verify` com
  `SPRING_PROFILES_ACTIVE=docker`. Ou seja, a suíte inteira já passa hoje contra Postgres real
  (banco novo a cada job, por causa do `docker compose down -v`).
- **Isolamento entre testes:** a maioria das classes `@SpringBootTest` é `@Transactional`
  (rollback). As exceções commitam de verdade: Cucumber da CLI (limpa via `CucumberHooks`), o
  Cucumber de validação (`ValidationSteps` apaga os ids que emitiu),
  `GenerateCommandRotationAtomicityTest` e `DeployoApiKeyApplicationTests`. Com H2 em memória,
  qualquer resto sumia no fim do processo. Com o volume `db_data` do compose, o resto persiste
  entre execuções locais.
- **Postgres do ambiente sandbox:** o ambiente sandbox da Claude é o mesmo usado pelo
  `jogo-acoes`. Ele já tem PostgreSQL 16 nativo (fora de Docker), provisionado por um script de
  setup do próprio ambiente que não fica versionado neste repositório. Esse script passa a
  criar também o banco do api-key, no cluster `16/main` (porta 5432), ao lado do `jogo_acoes`:

  ```bash
  # --- deployo_api_key (api-key: docker-compose.yml, serviço db) ---
  # Mesmo cluster/porta do jogo_acoes: o api-key usa localhost:5432/deployo_api_key tanto no
  # perfil docker quanto no sandbox, então as URLs/credenciais ficam idênticas.
  if ! psql_as_postgres -p 5432 -tAc "SELECT 1 FROM pg_roles WHERE rolname='deployo_api_key_admin'" | grep -q 1; then
    psql_as_postgres -p 5432 -c "CREATE ROLE deployo_api_key_admin WITH LOGIN PASSWORD 'deployo_api_key_admin';"
  fi
  if ! psql_as_postgres -p 5432 -tAc "SELECT 1 FROM pg_database WHERE datname='deployo_api_key'" | grep -q 1; then
    psql_as_postgres -p 5432 -c "CREATE DATABASE deployo_api_key OWNER deployo_api_key_admin;"
  fi
  ```

  `psql_as_postgres` é a função que o script já define
  (`runuser -u postgres -- psql -v ON_ERROR_STOP=1 "$@"`), e `pg_ctlcluster 16 main start`
  já roda antes desse bloco. As duas verificações são separadas, então rodar o script de novo
  depois de uma falha no meio completa o que faltou. O `CREATE DATABASE` fica num `-c` próprio
  porque não pode rodar dentro de uma transação.

  Uma diferença em relação ao compose: lá `deployo_api_key_admin` é o `POSTGRES_USER`, logo
  superusuário. Aqui ele é só dono do banco, e no Postgres 16 o schema `public` pertence ao
  dono do banco (`pg_database_owner`). Isso basta para as migrations atuais (V1, V2), que só
  criam e alteram tabelas. Uma migration futura que exija superusuário (ex.: `CREATE EXTENSION`)
  funcionaria no compose e falharia no sandbox.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como o `application.yml` de teste aponta para Postgres sem depender de profile? | Fixar URL, usuário, senha e driver diretamente nesse arquivo (os valores de `application-docker.yml`, mantendo os placeholders `${SPRING_DATASOURCE_*:...}`) + `flyway.locations: classpath:db/migration` | resolvida | Elimina o fallback silencioso: `mvn test` sem variável nenhuma já exige Postgres. Os placeholders preservam a possibilidade de apontar para outro host ou porta por ambiente. Mesma decisão do `jogo-acoes` 05-028. |
| `application-docker.yml` de **teste** (core, validation) continua existindo? | Não: removidos | resolvida | Depois da mudança acima, os dois ficariam idênticos ao `application.yml` de teste. Manter um arquivo que não muda nada é ruído. |
| `application-docker.yml` da CLI (`src/main`) continua? | Sim, mas sem `driver-class-name`/`flyway.locations` explícitos nem os comentários sobre H2 | resolvida | É o perfil de execução real da CLI contra o compose, e continua tendo papel próprio. Os campos explícitos existiam só para vencer os valores de H2 do `application.yml` de teste e viram redundância. |
| `SPRING_PROFILES_ACTIVE=docker` continua no CI? | Sim | resolvida | Sem efeito no datasource dos testes de core e validation (o perfil de teste deixa de existir ali), mas a CLI ainda tem `application-docker.yml` em `src/main`, e o CI deve exercitar o perfil `docker` de verdade. É uma linha que não custa nada e mantém o CI igual ao uso documentado. |
| Mecanismo de skip quando não há Postgres? | **Não.** Postgres é considerado sempre disponível (compose local e no CI; nativo ou compose no sandbox). Sem ele, os testes falham com erro de conexão | resolvida | Mesma conclusão revista no `jogo-acoes` 05-028: um skip baseado em variável de ambiente tem falso-skip e falso-erro, e Postgres não é uma dependência opcional deste projeto. Falhar alto é o comportamento correto para infraestrutura ausente. |
| `DeployoApiKeyApplicationTests` sem H2: como manter o "banco isolado"? | Rodar contra o mesmo Postgres (sem sobrescrever URL nem driver) e apagar no fim a chave que gerou (pelo `client_name` único do teste) | resolvida | O isolamento via H2 separado só existia para não sujar o estado de outros testes. Limpar o que o próprio teste criou dá o mesmo resultado num banco persistente, e o smoke test passa a provar o boot contra o banco de verdade. |
| `ApiKeyCliRunnerTest`: o que entra no lugar de `jdbc:h2:mem:unused`? | Um argumento neutro qualquer (ex.: `--some-flag=value`) | resolvida | O teste não sobe Spring nem abre conexão: o argumento só precisa ser uma palavra de comando desconhecida. A URL H2 era só um exemplo e não tinha papel nenhum. |
| `application-sandbox.yml`: para qual Postgres aponta? | O Postgres nativo do ambiente sandbox, no cluster `16/main` (porta 5432) que o `jogo-acoes` já usa, com banco `deployo_api_key` e role `deployo_api_key_admin` criados pelo script de setup do ambiente (ver "Contexto técnico"). URL, usuário e senha **idênticos** aos de `application-docker.yml` (`localhost:5432/deployo_api_key`), só o processo Postgres por trás muda (nativo em vez de container) | resolvida | Um ambiente sandbox só para os dois projetos, em vez de um cluster ou porta por projeto. Um cluster comporta vários bancos, e o 5432 já existe. Alternativas descartadas: cluster próprio em outra porta (mais um processo e uma URL diferente do `docker` sem ganho nenhum); `sandbox` exigindo `docker compose up db` (não há Docker no sandbox); remover o perfil `sandbox` (ainda é o perfil padrão da CLI e o único que roda no ambiente da Claude). |
| O que acontece com `db/migration-h2`? | Removido | resolvida (depende de T004) | Sem perfil nenhum usando H2, a árvore fica morta. Só pode sair depois que o `sandbox` deixar de usá-la. |

## Estrutura de módulos/pacotes

Nenhuma classe ou pacote novo. Arquivos tocados:

```
pom.xml (módulos)
  api-key-core/pom.xml, api-key-validation/pom.xml, api-key-cli/pom.xml
                                           (remove com.h2database:h2)
api-key-core/
  src/main/resources/db/migration-h2/      (removido)
  src/test/resources/application.yml       (H2 -> Postgres real, fixo)
  src/test/resources/application-docker.yml (removido)
  src/test/java/.../ApiKeysMigrationTest.java (só comentário)
api-key-validation/
  pom.xml                                   (também o comentário "on H2")
  src/test/resources/application.yml       (H2 -> Postgres real, fixo)
  src/test/resources/application-docker.yml (removido)
api-key-cli/
  src/main/resources/application-sandbox.yml (H2 -> Postgres real -- decisão em aberto)
  src/main/resources/application-docker.yml  (tira campos/comentários que só existiam por H2)
  src/test/resources/application.yml       (H2 -> Postgres real, fixo)
  src/test/java/.../DeployoApiKeyApplicationTests.java (sem H2; limpa a chave gerada)
  src/test/java/.../ApiKeyCliRunnerTest.java           (argumento neutro)
  src/test/java/... (comentários que dizem "H2")
memory/constitution.md                      (sandbox não é mais "H2 embarcado")
README.md                                   (rodar testes exige `docker compose up -d --wait db`)
```

## Riscos e trade-offs

- **Rodar testes localmente passa a exigir `docker compose up -d --wait db` antes**, sempre.
  Perde-se o `mvn test` sem infraestrutura nenhuma. Troca aceita conscientemente: é o que a spec
  pede.
- **O banco local persiste entre execuções (volume `db_data`).** Um teste que commita e não
  limpa passa a deixar resíduo que pode quebrar uma execução seguinte, o que nunca acontecia com H2
  em memória nem no CI (`down -v`). Mitigação: T008 roda a suíte duas vezes seguidas sem derrubar
  o banco, e qualquer teste que falhar na segunda rodada ganha a limpeza que faltava.
- **O mesmo Postgres local é usado pela CLI de verdade (`docker`) e pelos testes.** Rodar a suíte
  pode coexistir com chaves criadas manualmente (ex.: via Adminer, spec 007), e testes que listam
  "todas as chaves" podem ver dados alheios. Os testes atuais já filtram por `client_name` único
  por execução, mas vale conferir no T008.
- **O sandbox depende de infraestrutura fora do repositório** (o script de setup do ambiente).
  Se o script mudar ou não rodar, o `sandbox` falha com erro de conexão e nada no código avisa
  antes. É o mesmo risco aceito no `jogo-acoes`. Mitigação: o bloco do api-key fica registrado
  acima, em "Contexto técnico", para poder ser reaplicado.
- **Os dois projetos dividem o mesmo cluster no sandbox.** Como os bancos são separados
  (`jogo_acoes`, `deployo_api_key`), não há colisão de dados. Mas parar ou recriar o cluster
  `16/main` afeta os dois.
