# Plan: Tornar os cenários Gherkin executáveis

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Projeto Maven/Spring Boot já com uma suíte JUnit 5 extensa (84 testes antes desta spec). Esta
spec acrescenta Cucumber como um segundo `TestEngine` descoberto pelo mesmo `mvn verify` —
via `cucumber-junit-platform-engine`, que expõe os cenários `.feature` à JUnit Platform (o
mesmo mecanismo de descoberta/execução que já roda os testes `@Test` existentes), em vez de um
executor separado.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Qual framework/versão | Cucumber 8.0.3 (`cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine`) + `junit-platform-suite` 6.0.3 (mesma versão do JUnit Platform já gerenciada pelo `spring-boot-starter-parent`) | resolvida | Framework padrão de mercado para Gherkin executável em Java; `cucumber-spring` dá acesso aos beans reais (`GenerateCommand`, `ApiKeyRepository` etc.) do mesmo jeito que os testes `@SpringBootTest` já fazem — sem reimplementar nada. |
| Como os cenários acessam os comandos reais | `@CucumberContextConfiguration` + `@SpringBootTest(classes = DeployoApiKeyApplication.class)`, um `ApplicationContext` só, compartilhado pela suíte inteira | resolvida | Mesmo padrão dos testes JUnit (`GenerateCommandTest` etc.); subir um contexto por cenário seria lento sem necessidade — nenhum cenário precisa de configuração de bean diferente, exceto o pepper ausente (ver linha abaixo). |
| Como isolar dados entre cenários (sem um `@Transactional` por cenário, que o Cucumber não dá de graça) | Cada nome de cliente citado no `.feature` recebe um sufixo aleatório por cenário, aplicado de forma transparente pelos steps (`ScenarioState.qualifyClient`) | resolvida | `cucumber-spring` não envolve cada cenário na própria transação revertida ao final, do jeito que `@Transactional` faz para um teste JUnit — sem isolamento, dados de um cenário poderiam ser enxergados por outro no mesmo H2 compartilhado. Sufixar o nome do cliente é mais simples que orquestrar reversão de transação manual por cenário, e os cenários de `list` (que não filtram por cliente) já são escritos para checar presença/ausência de uma chave específica, não contagem total — imune a dados de outros cenários. |
| Onde mora o estado compartilhado entre classes de step (saída capturada, exit code, mapa de "id 3" → id real) | `ScenarioState`, bean Spring **singleton** comum, resetado por um hook `@Before` do Cucumber antes de cada cenário | resolvida | Tentativa inicial usou `@ScenarioScope` (o padrão documentado do `cucumber-spring` para isso) — na prática, os campos liam `null` ao acessar a mesma instância a partir de uma classe de step diferente da que escreveu (o proxy de escopo não resolveu o alvo de forma confiável entre classes). Como o Cucumber roda cenários sequencialmente nesta suíte (sem paralelismo configurado), um singleton resetado em `@Before` é igualmente seguro e muito mais simples de depurar. |
| Como simular falha de persistência para o cenário de atomicidade de `003` ("persisting the old-key revocation fails") | `@MockitoSpyBean ApiKeyRepository` declarado em `CucumberSpringConfiguration`, com `doThrow` configurado por um step (`Given the database rejects the next update to the key labeled...`) e `Mockito.reset(repository)` num hook `@After` de toda execução | resolvida | Mesmo racional de `GenerateCommandRotationAtomicityTest` (JUnit) — um spy em vez de um mock completo, para só a chamada específica falhar e todo o resto continuar passando pelo repositório real. Como a suíte Cucumber usa UM contexto (e portanto um único spy) para todos os cenários, o `reset()` pós-cenário é obrigatório para a falha simulada não vazar para o próximo cenário. |
| Como verificar cenários de `list` | Contra a tabela impressa de verdade (saída de `stdout`), não reconsultando o repositório e recalculando o status esperado | resolvida | Reimplementar a lógica de derivação de status (`revoked` > `expired` > `active`) dentro do teste testaria o teste, não o comando real — ver `ListingSteps`. |
| Frases dos passos Given/When/Then | Vocabulário pequeno e reutilizável entre as quatro features (`client "X" has an active key labeled "Y"`, `the operator runs "..."`, `the exit code is N`, etc.), não uma tradução literal da prosa original do `spec.md` | resolvida | A prosa original (ex.: "Given the operator runs \"generate\" without \"--client\"") foi escrita como texto livre, não como Gherkin parametrizável — forçar uma tradução literal exigiria uma definição de step por cenário, sem reaproveitamento nenhum. Normalizar para um vocabulário comum é prática padrão ao tornar Gherkin executável; o nome e o comportamento esperado de cada `Scenario:` não mudou. |

## Estrutura de módulos/pacotes

Tudo em `src/test/` (é infraestrutura de teste, não faz parte do artefato de produção):

- `src/test/resources/features/*.feature` — um arquivo por spec (001-004).
- `src/test/java/dev/leilaalgarve/apikey/cucumber/`:
  - `RunCucumberTest` — ponto de entrada (`@Suite` do JUnit Platform).
  - `CucumberSpringConfiguration` — liga o Cucumber ao contexto Spring; declara o spy do
    repositório.
  - `CucumberHooks` — `@Before` (reseta `ScenarioState`) e `@After` (reseta o spy) globais.
  - `ScenarioState` — estado mutável compartilhado entre as classes de step.
  - `CommandDispatchSteps` — despacha `generate`/`revoke`/`list` por nome, decodifica a linha
    de comando entre aspas, passos genéricos de exit code/stdout/stderr.
  - `KeyLifecycleSteps` — cria/consulta chaves (ativa, expirada, revogada, agendada).
  - `ListingSteps` — asserts sobre a tabela impressa por `list`.

## Riscos e trade-offs

- A suíte Cucumber roda sequencialmente contra um H2 compartilhado por toda a execução — mesma
  limitação de escala já aceita em `003`/`004` (volume pequeno de chaves/clientes esperado
  pelo projeto).
- O aviso `"The classpath resource selector 'features' should not be used..."` aparece no log
  do `junit-platform-suite`/`cucumber` — não afeta descoberta nem execução (confirmado:
  42/42 cenários rodam e passam); aceito como ruído cosmético de versão, não investigado a
  fundo.
