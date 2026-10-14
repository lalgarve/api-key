# Plan: validate-api-key-cli

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Novo comando (`validate`) em `api-key-cli`, que passa a depender também de
`api-key-validation` (hoje só depende de `api-key-core`). Primeiro comando desta CLI que
precisa ler a **entrada padrão** — `generate`/`revoke`/`list` só leem `args`. A regra de
validação em si (`ApiKeyValidator`) já existe e não muda; este comando só é a casca de CLI em
cima dela.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como `validate` lê stdin sem mudar a assinatura de `CliCommand.execute(args, out, err)` já usada por `generate`/`revoke`/`list` (e por ~40 call sites de teste) | `CliCommand` ganha um **método `default`** de 4 argumentos — `execute(args, in, out, err)` — que delega para o de 3 (`execute(args, out, err)`) quando não sobrescrito. `ApiKeyCliRunner.run()` passa a chamar sempre a versão de 4 argumentos (com `System.in` de verdade). Só `ValidateCommand` sobrescreve o método de 4; os outros três comandos continuam implementando só o de 3, sem tocar numa linha. | resolvida | Extender a assinatura abstrata obrigaria mudar `generate`/`revoke`/`list` e todos os testes que chamam `.execute(args, out, err)` (~40 ocorrências, ver busca em `api-key-cli/src`), sem nenhum ganho pra eles — não leem stdin e nunca vão precisar. O método `default` isola o custo em quem realmente precisa da entrada nova. `ApiKeyCliRunnerTest` precisa de um ajuste pequeno (os stubs `when(comando.execute(any(), any(), any()))` passam a não bater com a chamada real, que agora é de 4 argumentos — stubar a versão de 4, não a de 3). |
| Package do novo comando | `dev.leilaalgarve.apikey.management` (mesma package de `RevokeCommand`/`ListCommand`) | resolvida | `api-key-validation` já é dona do nome de package `dev.leilaalgarve.apikey.validation` (`ApiKeyValidator`, `ApiKeyValidationResult`, `ApiKeyFailureReason`). Um `ValidateCommand` também em `dev.leilaalgarve.apikey.validation`, mas dentro do JAR de `api-key-cli`, seria um *split package* entre dois artefatos diferentes no mesmo classpath — evitável sem custo nenhum escolhendo outro nome. `.management` já reúne os outros comandos "de operação sobre chaves existentes" (`revoke`, `list`); `validate` se encaixa no mesmo papel. |
| Onde mora a tradução `ApiKeyFailureReason` → exit code/mensagem | Dentro de `ValidateCommand` (ex.: um `switch` sobre o enum), não na biblioteca | resolvida | Mesma decisão já tomada em `008-validate-api-key/plan.md`: a biblioteca devolve só o motivo, quem consome decide a apresentação. A CLI é só mais um consumidor — mapeia pro seu próprio formato (exit code + mensagem em `contracts/cli-commands.md`), do mesmo jeito que um serviço HTTP mapearia pra status/JSON. |
| Como os testes (unitário e Cucumber) fornecem stdin | `ByteArrayInputStream` — no teste unitário de `ValidateCommand`, passado direto pro `execute(args, in, out, err)`; no Cucumber, um novo campo em `ScenarioState` guarda os bytes da entrada do cenário atual, e `CommandDispatchSteps` passa isso pro dispatch de 4 argumentos | resolvida | Mesmo padrão já usado pra `out`/`err` (streams injetados por chamada, nunca fixados em `System.*` dentro do comando) — `in` segue a mesma forma, por simetria e testabilidade. |
| `ApiKeyValidator` precisa de algo novo de `api-key-cli` pro `Clock` (`008`'s fallback) | Nada — `api-key-cli` ainda não registra nenhum bean `Clock` (isso é `011-configurable-clock`, não implementada), então `ApiKeyValidator` cai no fallback `Clock.systemUTC()` que já tem hoje | resolvida | Já é o comportamento padrão de `ApiKeyValidator` sem um `Clock` do consumidor (`008-validate-api-key/contracts/validation-api.md`) — nada a fazer aqui; `validate` já nasce pronto para respeitar um `Clock` configurável assim que `011` existir (spec.md FR8), sem mudança neste comando. |

## Estrutura de módulos/pacotes

- `api-key-cli/pom.xml` ganha a dependência em `api-key-validation` (além da já existente em
  `api-key-core`).
- `CliCommand` (`dev.leilaalgarve.apikey`, `api-key-cli`): ganha o método `default` de 4
  argumentos descrito acima. Único arquivo de produção fora de `management`/`cucumber` que
  muda.
- `ApiKeyCliRunner`: passa `validateCommand` no construtor, entrada `"validate"` no mapa de
  comandos, chama sempre `execute(args, in, out, err)` (não mais a de 3).
- Novo: `dev.leilaalgarve.apikey.management.ValidateCommand` (`api-key-cli`) — lê a primeira
  linha de `in` (`BufferedReader`, já descartando espaços nas pontas), despacha pro
  `ApiKeyValidator` injetado, traduz o resultado pro exit code/mensagem do contrato.

## Riscos e trade-offs

- O método `default` em `CliCommand` deixa a interface um pouco menos "plana" (duas formas de
  implementar um comando, dependendo se ele precisa de stdin ou não) — aceito conscientemente
  pra não tocar em ~40 call sites de comandos que não precisam da entrada nova. Se um quarto
  comando algum dia também precisar de stdin, vale reconsiderar se o método de 3 argumentos
  ainda faz sentido como o "principal" da interface.
- `blackbox-tests/features/validate.feature` (`012-blackbox-cli-tests`) já existe, marcado
  `@pending` com o comentário "a PR que implementa [010] remove a tag" — faz parte desta
  implementação remover a tag e confirmar que os cenários passam de verdade contra o jar
  empacotado.
