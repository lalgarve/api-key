# Spec: Refatorar pacote base `io.deployo` → `dev.leilaalgarve`

**Status:** aprovada
**Issue:** #16

## Resumo

Renomear o pacote Java raiz de todo o código deste projeto de `io.deployo` para
`dev.leilaalgarve` — mesma mudança já aplicada no `jogo-acoes`
(`specs/05-001-refactor-pacote-base`). Mudança puramente mecânica, sem alteração de
comportamento.

## Motivação

O pacote `io.deployo` foi escolhido presumindo posse do domínio `deployo.io` (convenção de
nomear pacotes Java pelo domínio invertido). Esse domínio não está mais disponível — manter o
pacote assim não reflete mais a titularidade real. `dev.leilaalgarve` já é o pacote base
adotado no `jogo-acoes`; este projeto, parte do mesmo ecossistema, segue a mesma convenção.

## Cenários (comportamento esperado)

Não aplicável — mudança estrutural, sem comportamento novo observável. Critério de aceite: a
suíte de testes existente (`mvn verify`) continua passando 100%, com a mesma cobertura, sem
nenhuma asserção alterada — só pacote muda.

## Requisitos funcionais

- Todo pacote que hoje começa com `io.deployo` passa a começar com `dev.leilaalgarve`,
  preservando a subestrutura existente: `io.deployo.apikey` → `dev.leilaalgarve.apikey`,
  `io.deployo.apikey.issuance` → `dev.leilaalgarve.apikey.issuance`,
  `io.deployo.apikey.management` → `dev.leilaalgarve.apikey.management`.
- Diretórios de código-fonte (`src/main/java`, `src/test/java`) refletem a nova estrutura de
  pastas correspondente ao novo pacote.
- Todas as referências textuais ao pacote antigo são atualizadas: imports, javadoc/comentários
  que citem o pacote por nome, menções em `specs/*.md` que citem o pacote por nome (a própria
  documentação viva das features já implementadas).
- `pom.xml`: `groupId` muda junto com o pacote Java, de `io.deployo.apikey` para
  `dev.leilaalgarve.apikey` — mesma decisão já tomada no `jogo-acoes` (ver "Decisões em
  aberto" de `05-001-refactor-pacote-base` lá: o `groupId` acompanha o pacote Java mesmo sem
  publicação em repositório Maven, por coerência entre pacote e coordenada). `artifactId`
  (`deployo-api-key`) não muda — não é o pacote Java, é só o nome do artefato/repositório.

## Requisitos não-funcionais

Nenhum além de manter o build e a suíte de testes verdes — não é uma mudança de performance,
segurança ou comportamento.

## Fora de escopo

- Renomear artefatos que não sejam pacote Java: nome do banco de dados/usuário
  (`deployo_api_key`/`deployo_api_key_admin` em `docker-compose.yml`/`application-docker.yml`),
  nome do repositório, nome do artefato Maven (`artifactId`). Só o pacote Java e o `groupId`
  estão no escopo desta spec — mesmo recorte do `jogo-acoes`.
- Qualquer mudança de comportamento, CLI, schema de banco ou migration já aplicada.
- Entradas já escritas em `docs/context/diario.md` não são reescritas (é um registro
  histórico, não documentação de produto — ver `memory/constitution.md`) — só uma entrada nova
  documenta esta mudança.

## Decisões em aberto

Nenhuma — mesmas decisões já tomadas e registradas no `jogo-acoes`
(`specs/05-001-refactor-pacote-base/spec.md`) se aplicam aqui sem ambiguidade: o `groupId`
muda junto, e não há módulo adicional (equivalente ao `email-lambda` lá) neste repositório.
