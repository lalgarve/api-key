# Plan: blackbox-cli-tests

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`mvn package` gera o jar executável `api-key-cli/target/api-key-<versão>.jar` (Spring Boot
repackage, `finalName` em `api-key-cli/pom.xml`). A CLI sobe o contexto Spring, conecta no
PostgreSQL (perfil `sandbox` por padrão, ou `docker`) e o `ApiKeyCliRunner` escolhe o comando
por `args[0]`. Exit code 0 termina a JVM normalmente; qualquer outro código chama
`System.exit`.

Hoje o `ApiKeyCliRunner` conhece `generate`, `revoke` e `list`. `validate` (`010`) e as opções de
relógio (`011`) estão especificados, mas ainda não implementados.

Comportamentos do jar real observados ao rodá-lo em 2026-10-03 (`java -jar ... list`), que os
testes dentro da JVM nunca veem:

- **O banner do Spring Boot e os logs de INFO saem em stdout**, misturados com a saída do
  comando. Um `stdout is "..."` nunca passaria sem tratar isso.
- **Quando `JAVA_TOOL_OPTIONS` está definida, a JVM imprime `Picked up JAVA_TOOL_OPTIONS: ...`
  em stderr.** O ambiente sandbox da Claude define essa variável (proxy), então todo
  `stderr is "..."` falharia lá.
- **Falha ao subir o contexto (por exemplo, Postgres fora do ar) sai com exit code 1**, o mesmo
  código de erro de uso dos comandos.
- Cada execução leva alguns segundos, quase todos para subir o Spring.
- **A chave impressa por `generate` é `dak_` seguido de 43 caracteres base64url** (`ApiKeyFormat`),
  não os 64 hexadecimais do exemplo no contrato de `001-generate-api-key`.
- **Sem locale no ambiente, o `—` da saída de `generate` vira `?`.**

Com `SPRING_MAIN_BANNER_MODE=off`, `LOGGING_LEVEL_ROOT=ERROR`, `LANG=C.UTF-8` e sem
`JAVA_TOOL_OPTIONS`, `generate` e `list` contra o Postgres do sandbox imprimiram só a saída do
contrato em stdout e nada em stderr (verificado em 2026-10-03).

O ambiente sandbox tem Python 3.11 e alcança o PyPI pelo proxy. O runner do GitHub Actions
(`ubuntu-latest`) tem Python 3 e Docker.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Framework BDD em Python | `behave` 1.3.x, fixado em `requirements.txt` | resolvida | É um runner de Gherkin puro: `.feature` + `steps/` + `environment.py` com hooks, filtro por tag (`--tags "not @pending"`) e relatório de console com o passo que falhou. `pytest-bdd` exigiria um arquivo de teste por feature ligando cenários ao pytest, e o ganho (fixtures do pytest) não é necessário numa suíte que só roda processos |
| Como rodar a CLI | `subprocess.run([java, "-jar", jar, *args], input=stdin, capture_output=True, text=True, timeout=120, env=env)` num único módulo `cli.py`, que devolve um objeto `CliResult(args, exit_code, stdout, stderr)` | resolvida | Um só ponto que monta o processo: timeout evita cenário travado (por exemplo, `validate` esperando stdin); `CliResult` é o que os passos de asserção e o relatório de falha (spec FR8) recebem |
| Onde está o jar | Variável `API_KEY_CLI_JAR`, se definida; senão o único `api-key-cli/target/api-key-*.jar` que não termina em `.original`. Se não achar exatamente um, `before_all` aborta com uma mensagem dizendo para rodar `mvn package` | resolvida | Spec FR2. Não fixa a versão no código da suíte |
| Variáveis de ambiente do processo filho | Montadas do zero a partir de uma lista permitida: `PATH`, `JAVA_HOME`, `SPRING_PROFILES_ACTIVE` e `SPRING_DATASOURCE_*` herdados; `LANG=C.UTF-8`; `API_KEY_HMAC_PEPPER` com um valor fixo de teste; mais as duas de log abaixo. Um passo pode remover ou sobrescrever uma variável só para aquele comando | resolvida | Deixa fora `JAVA_TOOL_OPTIONS` (e o aviso `Picked up ...` em stderr), torna o cenário de pepper ausente trivial e garante que nada do ambiente de quem roda vaza para o teste. A CLI não acessa rede, então não precisa do proxy. `LANG` é fixo porque, sem locale, a JVM troca o `—` da saída de `generate` por `?` |
| Banner e logs do Spring em stdout | O processo filho recebe `SPRING_MAIN_BANNER_MODE=off` e `LOGGING_LEVEL_ROOT=ERROR`, por variável de ambiente (relaxed binding do Spring Boot), sem mudar a CLI | resolvida | Spec, "Requisitos não-funcionais": nenhuma mudança no código Java. `ERROR` ainda mostra uma falha ao subir o contexto, só que em stdout, o que aparece no relatório de falha. O operador real continua vendo banner e logs em stdout; ver "Riscos" |
| Isolamento entre cenários | Cada cenário gera um sufixo aleatório (`uuid4().hex[:8]`) em `before_scenario`; todo nome de cliente nos passos passa por `client("acme")` → `acme-<sufixo>`. O passo `When I run "..."` substitui `{client}` e `{key_id}` antes de executar, e todo `list` dos passos de preparo leva `--client` | resolvida | Spec FR6, sem precisar apagar nada. Rodar a suíte duas vezes seguidas no mesmo banco não muda o resultado |
| Como achar o `id` de uma chave | Rodar `list --client <cliente> --status all` e ler a tabela por cabeçalho (`ID`, `CLIENT`, `CREATED_AT`, `EXPIRES_AT`, `REVOKED_AT`, `STATUS`, colunas separadas por dois ou mais espaços) | resolvida | Spec FR4: só pela CLI. Como o cliente é único no cenário, a tabela tem só as chaves dele |
| Como capturar a chave em texto puro | Regex `^dak_[A-Za-z0-9_-]{43}$` (linha inteira) no stdout de `generate`; guardada em `context.keys[label]` | resolvida | Mesmo formato de `ApiKeyFormat` (`api-key-core`): prefixo `dak_` e 43 caracteres base64url. O exemplo do contrato de `001` mostra 64 hexadecimais, mas o jar real imprime base64url; a suíte segue o jar. Nada é gravado em arquivo (spec FR9) |
| Chave expirada ou "no passado" | `generate --validity-days N --clock-start <data>` com uma data calculada no passo (`hoje - N - 10 dias`) | resolvida | Spec FR4. Depende de `011`; enquanto não estiver implementada, os cenários que usam isso levam `@pending` |
| Cenários pendentes | Tag `@pending` em cenário ou feature; o comando padrão roda com `--tags "not @pending"`; `--include-pending` no script roda tudo | resolvida | Spec FR10. A tag sai no PR que implementa `010`/`011` |
| Mascarar a chave no relatório de falha | `CliResult.__str__` troca toda ocorrência de `dak_[A-Za-z0-9_-]{43}` por `dak_****`; as mensagens de `assert` usam sempre esse `__str__` | resolvida | Spec FR8 e FR9 juntos: falha mostra tudo, menos a chave |
| Comando local | `blackbox-tests/run.sh`: cria `.venv` na pasta se não existir, instala `requirements.txt`, roda `behave` com os argumentos recebidos. `.venv/` no `.gitignore` | resolvida | Spec FR12 e "Requisitos não-funcionais" (nada global) |
| Workflow do CI | `.github/workflows/blackbox.yml`, só `workflow_dispatch`, com input opcional `include_pending`. Passos: checkout, Java 21, Python 3.11, `docker compose up -d --wait db`, `mvn -B package -DskipTests`, `blackbox-tests/run.sh` com `SPRING_PROFILES_ACTIVE=docker`, `docker compose down -v` | resolvida | Spec FR11 e decisão da Leila: passo próprio, só quando solicitado. `-DskipTests` porque a suíte Java já tem o próprio check; aqui só o jar interessa |

## Estrutura de módulos/pacotes

Tudo novo fica em `blackbox-tests/`, fora do reactor Maven. Nenhum arquivo Java muda.

```
blackbox-tests/
  README.md                 # como rodar, o que precisa estar de pé
  requirements.txt          # behave==1.3.x, pytest (testes de blackbox/)
  run.sh                    # venv + behave
  behave.ini                # paths, formato, tags padrão
  features/
    harness.feature         # cenários de spec.md, "Black-box test harness"
    generate.feature
    revoke.feature
    list.feature
    validate.feature        # @pending até 010
    clock.feature           # @pending até 011
    environment.py          # before_all (acha o jar), before_scenario (sufixo, contexto)
    steps/
      run_steps.py          # When I run "..." [with ... on stdin]
      setup_steps.py        # Given client "x" has an active/revoked/expired key ...
      assert_steps.py       # Then the exit code is / stdout is / stderr is / contains
  blackbox/
    cli.py                  # localiza o jar, monta env, roda o processo, CliResult
    list_table.py           # parse da tabela de list
```

`blackbox/` é um pacote Python comum importado pelos passos, testável sem `behave`.

## Riscos e trade-offs

- **Banner e logs em stdout no uso real.** A suíte desliga os dois por variável de ambiente, então
  ela não prova o stdout exato que um operador vê sem essas variáveis. Esse stdout hoje traz o
  banner e logs de INFO antes da saída do comando, o que atrapalha scripts que leem stdout
  (por exemplo, `validate` em `010`). Corrigir isso na CLI (banner desligado e logs em stderr
  no `application.yml`) é uma mudança de produto, fora desta spec; fica como sugestão de spec
  própria.
- **Exit code 1 ambíguo.** Uma falha de infraestrutura (Postgres fora do ar, jar quebrado) sai
  com 1, igual a um erro de uso. Para não confundir os dois, `before_all` roda um
  `list --client <sufixo>` de sanidade e aborta a suíte inteira, com stdout e stderr, se ele não
  sair com 0.
- **Tempo de execução.** Cada comando sobe o Spring (alguns segundos). Os passos de preparo usam
  o mínimo de comandos (uma chave por `generate`, uma leitura de `list` só quando o `id` é
  necessário). Rodar em paralelo está fora de escopo (spec).
- **Linhas acumulando no banco.** A suíte não apaga nada (a CLI não tem comando para isso). Com
  cliente único por cenário, isso não afeta resultados; o banco local pode ser recriado com
  `docker compose down -v`.
- **Data passada para chave expirada.** `--clock-start` com data calculada a partir do relógio
  da máquina que roda a suíte. Se esse relógio estiver muito diferente do relógio do processo
  Java (são a mesma máquina), o cálculo erra; não é um risco real aqui, só uma premissa.
- **Python 3.11.** É a versão mínima por causa do sandbox; o código não usa nada mais novo.
