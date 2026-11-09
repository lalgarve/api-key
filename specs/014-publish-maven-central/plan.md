# Plan: publish-maven-central

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Maven multi-módulo: `api-key-parent` (POM), `api-key-core`, `api-key-validation` e `api-key-cli`,
  com `spring-boot-starter-parent` 4.1.0 como parent.
- O `spring-boot-starter-parent` declara `url`, `licenses` (Apache 2.0), `developers` e `scm` do
  próprio Spring. Sem declaração nossa, esses valores seriam herdados e o POM publicado diria que a
  biblioteca é Apache 2.0, quando o `LICENSE` é GPL-3.0.
- `release.yml` publica com `mvn deploy` no GitHub Packages (`distributionManagement`, servidor
  `github`) e recusa uma tag já existente.
- O Central não permite substituir nem apagar uma versão publicada.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Qual versão publicar | 1.1.1 | resolvida (Leila, 2026-11-09) | A tag `v1.1.0` não tem os metadados exigidos: publicar 1.1.0 geraria artefatos diferentes da tag |
| De onde publicar | Workflow "Release" | resolvida (Leila, 2026-11-09) | Já roda os testes antes de publicar e cria a tag. Alternativa: `mvn deploy` no WSL, sem esses controles |
| Como enviar ao Central | `central-publishing-maven-plugin`, com `publishingServerId` `central`, `autoPublish` e `waitUntil=published` | resolvida | É o plugin oficial do Central Portal. Com `waitUntil=published`, o workflow só segue para a release depois de os artefatos estarem publicados |
| Onde ficam sources, javadoc e GPG | Num profile `release`, ativado só no workflow | resolvida | Mantém o build comum sem GPG (cenário 5) |
| GitHub Packages | Sai (`distributionManagement` e `packages: write`) | resolvida | Ninguém consome: o `jogo-acoes` evitou esse caminho porque exige token até para ler. Alternativa: publicar nos dois, mantendo um canal sem uso |
| CLI fora do Central | `excludeArtifacts` do plugin; sai o `maven.deploy.skip` do módulo | resolvida, a confirmar na T004 | O `maven.deploy.skip` atendia o `maven-deploy-plugin`, que o plugin do Central substitui. A CLI é o último módulo do reator, e o envio do pacote acontece nele: excluir o artefato mantém o módulo participando do envio |
| Como a chave chega ao Maven | `gpg-private-key` e `gpg-passphrase` do `actions/setup-java`, `server-id: central` | resolvida | O `setup-java` importa a chave e gera o `settings.xml` no runner, sem credencial em disco fora dele |
| Onde ficam os secrets | Environment `Release` do GitHub, declarado no job | resolvida (Leila, 2026-11-09) | Os secrets foram criados nesse environment: sem `environment: Release` no job, o workflow não os enxerga |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

Só `pom.xml` (raiz e módulos), `.github/workflows/release.yml` e `README.md`. Nenhuma mudança em
código Java.

## Riscos e trade-offs

- **A publicação é irreversível.** Um erro nos metadados só se corrige com outra versão. A T004
  gera e confere o pacote sem enviá-lo antes da primeira execução real.
- **Falha entre o Central e a release do GitHub.** Se a criação da tag ou da release falhar depois
  da publicação, rodar o workflow de novo é recusado pelo Central, que não aceita a mesma versão.
  A saída é criar a release à mão com os jars, ou publicar a versão seguinte.
- **Javadoc pode falhar em comentários hoje aceitos**, e isso só apareceria no profile `release`.
  A T004 cobre isso antes da release.
- **Dependência de um serviço externo na release:** indisponibilidade do Central faz o workflow
  falhar antes de criar a tag, sem efeito parcial.
