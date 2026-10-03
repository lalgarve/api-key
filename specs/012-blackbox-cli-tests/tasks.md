# Tasks: blackbox-cli-tests

Quebra `spec.md`/`plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `chore:` criar `blackbox-tests/` com `requirements.txt` (`behave` 1.3.x e `pytest` fixados), `behave.ini`, `run.sh` (cria `.venv`, instala, roda `behave` com `--tags "not @pending"` por padrão e `--include-pending` para tudo) e `.venv/` no `.gitignore` | — | | #<n> |
| T002 | `blackbox/cli.py`: localizar o jar (`API_KEY_CLI_JAR` ou `api-key-cli/target/api-key-*.jar` sem `.original`), montar o ambiente do processo filho pela lista permitida do `plan.md` (com `LANG=C.UTF-8`, banner desligado, log `ERROR`, pepper de teste, sem `JAVA_TOOL_OPTIONS`), rodar com stdin e timeout, devolver `CliResult` com `__str__` que mascara a chave | T001 | | #<n> |
| T003 | `blackbox/list_table.py`: ler a tabela de `list` por cabeçalho e a mensagem `No API keys found for the given filters.` | T001 | [P] | #<n> |
| T004 | Testes unitários (pytest, só para o pacote `blackbox/`) de T002 e T003: escolha do jar, ambiente montado, máscara da chave, parse da tabela com e sem linhas | T002, T003 | | #<n> |
| T005 | `features/environment.py`: `before_all` acha o jar e roda um `list` de sanidade (aborta a suíte com stdout/stderr se não sair 0); `before_scenario` cria o sufixo único e o estado do cenário | T002 | | #<n> |
| T006 | Passos genéricos: `When I run "..."` (com `{client}`/`{key_id}` substituídos), `... with "<texto>" on stdin`, `... with that key on stdin`, variável de ambiente removida ou trocada; `Then the exit code is N`, `stdout is "..."`, `stderr is "..."`, `... is empty`, `... contains "..."`, `neither stdout nor stderr contains the key`. Toda asserção falha mostrando o `CliResult` | T005 | | #<n> |
| T007 | Passos de preparo só pela CLI: chave ativa, sem validade, revogada, com revogação agendada, rotacionada (`--revoke-old-in-days`); captura da chave do stdout de `generate` e do `id` via `list` | T006, T003 | | #<n> |
| T008 | `harness.feature` com os cenários de `spec.md` ("Black-box test harness"), exceto o de chave expirada, que leva `@pending` até `011` | T007 | | #<n> |
| T009 | `generate.feature`: sucesso com e sem `--validity-days`, `--client` ausente e em branco, `--validity-days` inválido, pepper ausente (exit 2), stdout limpo e stderr vazio no sucesso | T007 | [P] | #<n> |
| T010 | `revoke.feature`: imediata, agendada com `--in-days`, `--id` ausente e não numérico, `--in-days` inválido, `id` inexistente (2), já revogada (3) | T007 | [P] | #<n> |
| T011 | `list.feature`: filtro padrão, `--status all/revoked`, `--status` inválido, `--revoking-within-days` válido, inválido e combinado com outro status, nenhum resultado. Cenários que precisam de chave expirada levam `@pending` até `011` | T007 | [P] | #<n> |
| T012 | `validate.feature` (`@pending` até `010`): todos os exit codes do contrato de `010`, com a chave por stdin, e a chave nunca aparecendo na saída | T007 | [P] | #<n> |
| T013 | `clock.feature` (`@pending` até `011`): aviso em stderr com stdout intacto, `--clock-offset-days` positivo e negativo, `--clock-start` data e instante, as duas opções juntas, valores inválidos | T007 | [P] | #<n> |
| T014 | `.github/workflows/blackbox.yml`: só `workflow_dispatch` (input `include_pending`), Java 21, Python 3.11, `docker compose up -d --wait db`, `mvn -B package -DskipTests`, `run.sh` com `SPRING_PROFILES_ACTIVE=docker`, `docker compose down -v` | T008 | [P] | #<n> |
| T015 | Documentar em `blackbox-tests/README.md` e no `README.md` da raiz: pré-requisitos (jar, Postgres, Python 3.11+), o comando local e como disparar o workflow | T014 | [P] | #<n> |
| T016 | Validar no sandbox: `mvn clean verify` verde (nada Java mudou), depois `mvn package -DskipTests` e `run.sh` duas vezes seguidas no mesmo banco, 100% verde sem `@pending`; disparar o workflow uma vez e confirmar verde | T009, T010, T011, T014 | | #<n> |
| T017 | Registrar a mudança numa entrada nova de `docs/context/diario.md` | T016 | [P] | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- T012 e T013 são escritos agora, como contrato, e ficam `@pending`. O PR que implementar `010`
  ou `011` tira a tag dos cenários correspondentes (inclusive os de T008 e T011) e roda a suíte.
- Nenhuma tarefa muda código Java. Se alguma precisar, é sinal de bug na CLI encontrado pela
  suíte: vira Issue própria, não entra aqui.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que a resolve for mesclado.
