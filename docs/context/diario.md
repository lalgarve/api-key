# Diário de desenvolvimento

Resumo diário do que foi feito, com os commits e Issues envolvidos. Ver "Diário de
desenvolvimento" em `memory/constitution.md` para o formato. Não é documentação de produto —
não deve ser publicada junto com o restante de `docs/`.

## 2026-09-04

**Resumo:** adotada a estrutura de Spec-Driven Development (SDD) para o projeto —
`memory/constitution.md` (convenções, adaptado do `desenvolvimento.md` do projeto
`jogo-acoes`), `templates/` para novas features, `specs/` para as specs vivas, e este
diário. Rastreamento de trabalho passa a usar Issues do GitHub, espelhando `tasks.md` de
cada feature. README atualizado com o contexto do projeto (parte do `jogo-acoes`,
API-KEY como padrão de autenticação entre APIs internas, admin UI fora de escopo por
enquanto), o papel deste repositório como exemplo de SDD para a disciplina, e a
divulgação de uso de ferramentas de IA.

**Commits:**
- `9a58b33` docs: adopt Spec-Driven Development structure
- `062a18e` docs: record commit hash in today's diary entry
- `868caac` docs: explain project context and disclose AI tool usage in README
- `805b7ea` docs: add Issues to the language convention table

**Issues:** —

## 2026-09-05

**Resumo:** primeira feature real do projeto — `specs/001-generate-api-key/` (spec, plan,
data-model, contrato de CLI e tasks) para o comando `generate`, que emite uma API-KEY para um
serviço consumidor e persiste só o hash HMAC-SHA256. Três decisões técnicas ficaram em
aberto em `plan.md` (motor de banco, mecanismo de acesso, ferramenta de migration), bloqueando
a tarefa T001. Criada a Issue-épico #2 no GitHub, espelhando `tasks.md`.

As três decisões em aberto foram resolvidas no mesmo dia: Spring Data JPA/Hibernate,
PostgreSQL (`docker`/CI) + H2 (`sandbox`) e Flyway — mesmo padrão do `jogo-acoes`,
priorizando consistência entre os dois projetos do portfólio sobre otimizar esta CLI
isoladamente. Adicionado também um prazo de validade opcional à chave (`--validity-days`,
inteiro positivo; se omitido, a chave não expira) — `expires_at` na tabela, verificação de
expiração em si continua fora de escopo (fica com a futura biblioteca de leitura).

Começada a implementação: T001 concluída — projeto Maven (Spring Boot 4.1.0, Java 21)
criado do zero com a migration Flyway de `api_keys`, perfis `sandbox` (H2) e `docker`
(PostgreSQL) espelhando o `jogo-acoes`. Validado nesta sessão contra H2/sandbox (sem Docker
disponível neste ambiente): `mvn test`, 6/6 passando, cobrindo inserção completa, `expires_at`
nulo, e rejeição de `service_name`/`key_hash`/`created_at` nulos e de `key_hash` duplicado.

Configurado o piso de cobertura (JaCoCo, 80% de linha em `mvn verify`) e o CI
(`.github/workflows/ci.yml`) subindo PostgreSQL real via `docker-compose.yml` e rodando a
suíte com `SPRING_PROFILES_ACTIVE=docker` — mesmo padrão do `jogo-acoes`. Cogitei excluir a
classe `DeployoApiKeyApplication` da cobertura (só tem uma linha de boilerplate do Spring
Boot) mas isso deixaria o JaCoCo analisando zero classes e passando vazio — em vez disso,
escrevi um smoke test real chamando `main()` diretamente, cobrindo a linha de verdade.

**Commits:**
- `6ddc76a` feat: add spec, plan and tasks for generate-api-key
- `c2c9351` decision: use Spring Data JPA/Hibernate, PostgreSQL+H2 and Flyway for generate-api-key
- `df28ebe` feat: add optional key validity period to generate-api-key
- `d9a53a9` feat: create the api_keys Flyway migration (T001)
- `b6ff341` docs: mark T001 done in tasks.md
- `2fe2dee` feat: enforce 80% JaCoCo coverage and run CI against real Postgres

**Issues:** #2 aberta (épico da feature 001; T000-T001 concluídas, T002-T008 pendentes)

**Nota:** o `docker-compose.yml`/CI não foram validados de ponta a ponta nesta sessão — sem
daemon Docker disponível neste ambiente. Sintaxe checada (`docker compose config`, YAML do
workflow), mas vale confirmar no primeiro run real de CI.

O primeiro CI real da PR #4 falhou, confirmando exatamente essa ressalva: com
`SPRING_PROFILES_ACTIVE=docker`, `application-docker.yml` sobrescreve
`driver-class-name` para `org.postgresql.Driver`, e o smoke test só sobrescrevia a URL do
datasource (para H2) — a aplicação tentou abrir a URL H2 com o driver do Postgres e quebrou.
Invisível localmente porque o perfil `docker` nunca tinha rodado de verdade antes do push.
Corrigido sobrescrevendo `driver-class-name` também, e portado o mesmo fix pro
`deployo-template-java` (mesmo padrão, mesmo bug latente, PR própria lá).

**Commits (continuação):**
- `b7051b6` fix: override datasource driver in the smoke test, not just the URL

## 2026-09-20

**Resumo:** retomando a sessão, descoberto que o fix do CI (`b7051b6`/`a2e3315` acima) nunca
chegou ao `main` — a PR #4 mesclou no commit anterior ao fix, deixando o `main` com CI
vermelho desde então (confirmado: os dois merges seguintes, #4 e #5, ficaram vermelhos).
Aberta a PR #6 reaproveitando a mesma branch para trazer o fix. Em paralelo, implementada a
T002: `ApiKeyGenerator` (pacote `io.deployo.apikey.issuance`, a frente de "Emissão" definida
em `plan.md`) — gera a chave com prefixo `dak_` + 32 bytes de entropia em base64url.

**Commits:**
- `80f978a` feat: generate a random API key with the dak_ prefix (T002)

**Issues:** #2 aberta (T000-T002 concluídas, T003-T008 pendentes); PR #6 aberta corrigindo o
CI do `main`.

PRs #6 e #7 mescladas (CI verde nas duas). Comparei este arquivo com o `memory/constitution.md`
atual do `jogo-acoes` (que migrou de `docs/context/desenvolvimento.md` pra esse mesmo caminho,
citando o `deployo-template-java` como referência) e portei duas seções genuinamente
aplicáveis aqui: "Status do sistema: pré-produção" (schema/contrato podem mudar livre,
sem migração de dado real, enquanto não há usuário real) e um esclarecimento em "Branches e
Pull Requests" sobre continuar na mesma branch entre sessões/ferramentas. O resto do que
mudou lá (numeração de iteração, coexistência com `docs/context/iteracao-N.md`, labels de
Issue específicos, carve-out de OpenAPI, nota sobre custo de API do Gemini) é complexidade
da escala do `jogo-acoes`, não pertinente aqui.

**Commits (continuação):**
- `e3538f7` docs: port pre-production status and branch-continuity sections from jogo-acoes

Esclarecido o modelo de implantação: cada serviço protegido roda sua própria cópia deste
projeto (mesmo container, banco próprio, nunca compartilhado) — então o parâmetro da CLI não
deveria nomear o serviço protegido (implícito na instância), e sim o **cliente** autorizado a
chamá-lo. Renomeado `--service`/`service_name` para `--client`/`client_name` em toda a spec,
plan, data-model, contrato de CLI, tasks, README e no schema (`V1` editado direto, sem `V2`,
seguindo a seção de pré-produção do `memory/constitution.md`). Exemplo trocado de
`email-service` para `jogo-acoes` (o cliente real) nos textos.

**Commits (continuação):**
- `6105bf7` decision: rename service to client, clarify per-service deployment
- `c044e1e` refactor: rename service_name to client_name in the api_keys schema

**Issues:** #2 aberta (T000-T002 concluídas, T003-T008 pendentes); PR #8 mesclada, PR #9
(esta renomeação) resolvendo conflito de merge com a #8 no próprio `diario.md`.

## 2026-09-22

**Resumo:** implementadas todas as tarefas restantes da feature `generate-api-key`
(T003-T008) — a feature está completa. `ApiKeyHasher` (HMAC-SHA256, pepper via
`API_KEY_HMAC_PEPPER`), `ApiKey`/`ApiKeyRepository` (persistência JPA), `GenerateCommand`
(orquestração completa: parsing manual de `--client`/`--validity-days`, geração → hash →
validade → persistência → impressão única da chave, retornando o exit code em vez de
chamar `System.exit` direto) e `ApiKeyCliRunner` (adaptador fino que só sai do processo via
`ProcessExiter` quando o código não é zero — decisão tomada especificamente para o smoke
test de `main()` continuar seguro em qualquer caminho de sucesso). README ganhou seção de
uso e o procedimento de backup do pepper (T008). `plan.md` fechado: nome da variável do
pepper, decisão de não usar lib de parsing de CLI, e o design do exit code documentados.

**Commits:**
- `760c957` feat: implement HMAC-SHA256 hashing for generated keys (T003)
- `7d3f282` feat: persist API keys via Spring Data JPA (T005)
- `2f7b2e8` feat: implement the generate CLI command (T004, T006, T007)
- `e0a3e58` decision: finalize pepper env var name and record CLI implementation decisions
- `02e8df3` docs: document HMAC pepper backup procedure and CLI usage (T008)
- `a9f8404` docs: mark T003-T008 done, close out spec.md status

**Issues:** #2 — todas as tarefas concluídas, pronta para fechar quando a PR mesclar.
