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
| `application-sandbox.yml`: para qual Postgres aponta? | — | **em aberto** | Ver "Decisões em aberto" em `spec.md`. Depende de existir (ou não) Postgres nativo com `deployo_api_key` no ambiente sandbox, e em qual porta. Bloqueia só T004 e T009. O resto da spec independe. |
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
- **Sandbox depende de infraestrutura fora do repositório**, se a decisão em aberto cair em
  Postgres nativo. Se o script de setup mudar ou não rodar, o `sandbox` falha com erro de conexão
  e nada no código avisa antes. É o mesmo risco aceito no `jogo-acoes`.
