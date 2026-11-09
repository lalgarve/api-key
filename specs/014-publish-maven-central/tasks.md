# Tasks: publish-maven-central

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Leila: criar a chave GPG, enviar a parte pública a um servidor de chaves e configurar `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY` e `GPG_PASSPHRASE` nos secrets do environment `Release` | — | [P] | a criar |
| T002 | `pom.xml` raiz: `url`, `licenses` (GPL-3.0), `developers` e `scm` próprios; profile `release` com sources, javadoc, GPG e `central-publishing-maven-plugin`, com a CLI excluída; sai o `distributionManagement` | — | [P] | a criar |
| T003 | `.github/workflows/release.yml`: `setup-java` com `server-id: central` e a chave GPG; `mvn -B deploy -DskipTests -Prelease` no lugar do deploy atual; sai `packages: write`. `README.md`: seção "Releases" e "Usar a biblioteca num serviço" | T002 | | a criar |
| T004 | Sem enviar nada: gerar o pacote com o profile `release`, uma chave GPG descartável e credenciais falsas, e conferir em `target/central-publishing` que tem `api-key-parent`, `api-key-core` e `api-key-validation`, com sources, javadoc e assinaturas, e sem `api-key-cli`; conferir no POM publicado a licença GPL-3.0 e o `mvn verify` sem GPG (cenário 5) | T002 | | a criar |
| T005 | Por PR, trocar a versão para 1.1.1 nos quatro `pom.xml` e no README | T003, T004 | | a criar |
| T006 | Leila: rodar o workflow "Release" no `main` (irreversível) | T001, T005 | | a criar |
| T007 | Verificar o cenário 2: projeto mínimo sem `settings.xml`, com repositório local vazio, resolve `api-key-validation:1.1.1`. Registrar aqui o resultado | T006 | | a criar |
| T008 | Registrar a publicação em `docs/context/diario.md` | T007 | | a criar |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.

## Registro da verificação

**T004 (2026-11-09, WSL):**

- `mvn verify` sem GPG nem credencial, contra Postgres real: `BUILD SUCCESS`, 155 testes na CLI, sem
  falhas (cenário 5).
- `mvn deploy -DskipTests -Prelease` com chave GPG descartável e credenciais falsas: o Central
  respondeu 401, sem aceitar nada. O pacote `target/central-publishing/central-bundle.zip` tem
  `api-key-parent`, `api-key-core` e `api-key-validation`, com sources, javadoc e assinaturas, e
  não tem `api-key-cli`. A assinatura do POM confere (`gpg --verify`).
- `-Dcentral.skipPublishing=true` não impediu a tentativa de envio nesta versão do plugin (0.12.0):
  por isso a validação depende de credenciais falsas.
