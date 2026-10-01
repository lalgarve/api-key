# Spec: Tornar os cenários Gherkin executáveis

**Status:** aprovada — implementada (T001-T011 concluídas)
**Issue:** #18

## Resumo

Os cenários Gherkin escritos em `specs/001-generate-api-key/spec.md` a
`specs/004-list-api-keys/spec.md` existiam só como prosa dentro do `spec.md` de cada feature —
nenhum arquivo `.feature`, nenhum Cucumber, zero testes rastreáveis a eles. Esta spec corrige
isso: cada cenário já escrito passa a ser um cenário Cucumber de verdade, executado por
`mvn verify`.

## Motivação

`memory/constitution.md` ("Cenários de comportamento") já previa as duas formas válidas de
escrever Gherkin — dentro do `spec.md` ou como arquivo `.feature` referenciado por ele — e
descreve seu papel como "contrato de aceite, não documentação a posteriori". Escrever a prosa
Gherkin e nunca executá-la contraria exatamente esse papel: nada garante que os cenários ainda
descrevem o comportamento real depois de qualquer mudança futura no código. Os testes JUnit já
existentes (`GenerateCommandTest`, `RevokeCommandTest`, `ListCommandTest` etc.) cobrem
comportamento equivalente, mas foram escritos de forma independente, sem rastreabilidade
1-para-1 com os cenários do `spec.md` — não são "os cenários rodando", são outro conjunto de
testes que por acaso testa coisas parecidas.

## Requisitos funcionais

- Cada `Scenario:` hoje presente nos blocos ```gherkin``` de `specs/001` a `specs/004` tem um
  cenário Cucumber correspondente em `src/test/resources/features/`, preservando o nome e o
  comportamento esperado do cenário original (frases dos passos podem ser normalizadas para
  uma gramática consistente e parametrizável — ver `plan.md` — mas o significado de cada
  `Scenario:` não muda).
- O texto Gherkin deixa de estar duplicado em dois lugares: a seção "Cenários" de cada
  `spec.md` (001-004) passa a referenciar o arquivo `.feature` correspondente em vez de
  reproduzir a prosa inline, evitando as duas cópias divergirem silenciosamente.
- Os testes JUnit já existentes permanecem — eles cobrem detalhes de implementação que o
  Gherkin não expressa (determinismo do HMAC, round-trip de persistência, a tabela de
  despacho da CLI, o rollback real de transação). Esta spec acrescenta a camada de aceite
  executável, não substitui a camada unitária.
- `specs/005-refactor-pacote-base` não ganha cenários — é uma mudança estrutural sem
  comportamento observável, nada para um cenário de aceite descrever.

## Requisitos não-funcionais

A suíte Cucumber roda junto com `mvn verify` (mesmo perfil `sandbox`/H2 usado pelos testes
JUnit) — não é um comando ou pipeline separado, para não criar uma segunda forma de "passar o
CI" que alguém possa esquecer de rodar.

## Fora de escopo

- Migrar os testes JUnit existentes para Cucumber.
- Paralelizar a execução dos cenários (a suíte roda sequencialmente; ver `plan.md`).
- Gerar relatórios HTML/Cucumber-specific de execução — o plugin `pretty` (saída de console)
  já é suficiente para este projeto pequeno.

## Decisões em aberto

Nenhuma — decisões técnicas (versão do Cucumber, isolamento entre cenários, como simular falha
de persistência) resolvidas em `plan.md`.
