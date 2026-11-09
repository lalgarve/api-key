# Spec: publish-maven-central

**Status:** rascunho
**Issue:** a criar
**Versão alvo:** 1.1.1 (sem mudança de código das bibliotecas)

## Resumo

O workflow "Release" passa a publicar `api-key-parent`, `api-key-core` e `api-key-validation` no
Maven Central, de onde qualquer serviço os baixa como uma dependência comum, sem credencial e sem
script de instalação.

## Motivação

Hoje as bibliotecas só estão no GitHub Packages, que exige token até para ler, e nos assets da
release. Por isso o `jogo-acoes` as instala no `~/.m2` com um script próprio, baixando da release
do GitHub. Esse passo extra já quebrou o build: quando o cache do Maven do Docker é apagado e as
camadas do build não, o `email-service` não acha a biblioteca (spec 05-062 do `jogo-acoes`).

O namespace `dev.leilaalgarve` já foi verificado no Central Portal (informado por Leila,
2026-11-09).

## Cenários (comportamento esperado)

1. **Publicação pelo workflow:** com a versão 1.1.1 no `main` e os secrets configurados, rodar o
   workflow "Release" termina com sucesso, e os três artefatos ficam disponíveis em
   `https://repo.maven.apache.org/maven2/dev/leilaalgarve/apikey/`.
2. **Consumo sem credencial:** um projeto Maven sem `settings.xml`, com `~/.m2` vazio e dependendo
   só de `api-key-validation:1.1.1`, resolve a biblioteca e suas dependências transitivas.
3. **CLI fora do Central:** `api-key-cli` não é publicada no Central e continua saindo como asset
   da release (`api-key-1.1.1.jar`).
4. **Teste falhando não publica:** se o `mvn verify` falhar, nada é enviado ao Central e nenhuma
   tag é criada.
5. **Build comum sem GPG:** `mvn verify`, sem chave GPG nem credencial do Central, continua
   passando, localmente e no CI.

## Requisitos funcionais

- Cada artefato publicado atende às exigências do Central: `name`, `description`, `url`,
  `licenses`, `developers` e `scm` no POM; jars de sources e de javadoc nas bibliotecas; assinatura
  GPG em todos os arquivos.
- O POM declara a licença real do projeto (GPL-3.0, arquivo `LICENSE`), a URL, os desenvolvedores
  e o SCM deste repositório, sem herdar os do `spring-boot-starter-parent`.
- A credencial do Central e a chave GPG vêm dos secrets do environment `Release` do GitHub
  (`CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`), e nenhuma é gravada
  no repositório.
- O GitHub Packages deixa de receber as bibliotecas.
- Os jars das bibliotecas e o da CLI continuam anexados à release do GitHub.
- O `README.md` passa a explicar o uso pela dependência do Central, sem `<repositories>` nem token.

## Requisitos não-funcionais

- A credencial do Central e a chave GPG só são necessárias no workflow "Release".
- Publicar só depois de todos os testes passarem, como já acontece hoje.

## Fora de escopo

- Publicar a CLI no Central.
- Republicar 1.0.x ou 1.1.0: a tag `v1.1.0` não tem os metadados exigidos.
- Mudanças em `api-key-core` ou `api-key-validation` além do POM.
- A troca da dependência no `jogo-acoes` (spec 05-062 daquele repositório).

## Decisões em aberto

Nenhuma.
