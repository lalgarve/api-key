# Como desenvolvemos software

Este documento descreve o **fluxo de trabalho e as convenções** usados neste projeto — não
as decisões técnicas específicas dele (essas ficam em `specs/*/plan.md`). A ideia é que este
arquivo seja **agnóstico de projeto**: pode ser copiado como ponto de partida para outro
repositório sem precisar reexplicar o processo do zero.

No vocabulário do Spec-Driven Development (SDD), este arquivo é a *constitution* do
projeto: o conjunto de princípios que toda `spec.md`/`plan.md` deve respeitar.

> Adaptado a partir do `docs/context/desenvolvimento.md` do projeto `jogo-acoes`, que usava
> uma filosofia semelhante (BDD + DER + OpenAPI escritos antes da implementação) sem chamá-la
> de SDD. As seções abaixo têm o mesmo espírito, com os nomes de arquivo ajustados para a
> estrutura `memory/` + `specs/` do SDD.

## Idioma

| O quê | Idioma |
|---|---|
| Código: identificadores, comentários, nomes de arquivo de código | Inglês |
| Mensagens de commit | Inglês |
| Issues e Pull Requests (título e descrição) | Inglês |
| Specs de comportamento (Gherkin, quando usado dentro de `spec.md`) | Inglês |
| Documentação de projeto (`README.md`, `docs/*.md`, `specs/**/*.md`) | Português |

Código em inglês porque é o padrão do ecossistema (bibliotecas, mensagens de erro,
convenções da linguagem). Documentação em português porque é o idioma da equipe — não faz
sentido traduzir decisões e raciocínio para um idioma que não é o nativo de quem escreve e
lê.

## Status do sistema: pré-produção

O sistema ainda não tem usuários reais nem dado em produção — está em pré-produção, e
continua assim até este status ser explicitamente revisado neste documento. Isso tem
consequências diretas em como `spec.md`/`plan.md` são escritas e implementadas:

- **Nenhuma migração de dado existente é necessária.** Uma coluna nova pode ser `NOT NULL`
  direto, sem `default`/sem virar `nullable` "para registros antigos" — não existe registro
  antigo real que dependa disso. Uma tabela pode ser recriada, uma coluna removida, sem plano
  de migração dos dados já lá.
- **Nenhuma preocupação de compatibilidade retroativa de contrato** (versionamento, período
  de depreciação, cliente antigo continuando a funcionar) — um contrato de interface
  (`contracts/`) pode mudar de forma incompatível entre specs, sem manter a forma anterior
  funcionando em paralelo.
- **Nenhum plano de rollback que preserve dado real** — uma migration Flyway nova pode fazer
  `DROP COLUMN`/`DROP TABLE` sem se preocupar com "e se alguém já tiver algo lá".
- Isso não dispensa manter a suíte de testes verde nem seguir o processo de specs antes do
  código — só remove a categoria de risco "o que fazer com dado/cliente já existente", que
  normalmente motivaria `nullable`s defensivos, migrações em duas fases, ou versionamento de
  contrato.

**Quando isso muda**: no primeiro uso real em produção (ex.: o serviço de e-mail passando a
validar chaves geradas por aqui), esta seção precisa ser atualizada (ou removida) — a partir
daí, specs que tocam schema/contrato voltam a precisar considerar migração de dado existente
e compatibilidade retroativa.

## Commits semânticos

Formato da primeira linha:

```
<tipo>: <resumo curto, no imperativo>
```

Tipos usados neste projeto (convenção Conventional Commits, mais um tipo próprio):

| Tipo | Quando usar |
|---|---|
| `feat` | Nova funcionalidade ou comportamento observável |
| `fix` | Correção de bug |
| `refactor` | Mudança estrutural que não altera comportamento (renomear, mover, reorganizar) |
| `test` | Adição/alteração de testes ou especificações |
| `docs` | Mudança só de documentação |
| `chore` | Manutenção sem impacto em código de produção (dependências, config de build) |
| `decision` | Registra uma decisão de arquitetura/design tomada, antes ou junto da implementação que ela habilita |

`decision` é a extensão específica deste fluxo: quando uma pergunta de arquitetura em aberto
(documentada previamente como pendente em `plan.md`) é resolvida, isso vira um commit
próprio, separado da implementação — mesmo que a decisão não mude nenhuma linha de código
sozinha. Isso deixa o histórico do git navegável como uma trilha de decisões, não só de
mudanças de código.

Esses mesmos tipos são usados como **labels de Issue** — ver "Rastreamento de trabalho via
Issues" abaixo — para que commit, Issue e PR falem o mesmo vocabulário.

### Corpo da mensagem

Uma mensagem de commit completa, para uma mudança não trivial, normalmente tem:

1. **Título**: `<tipo>: <resumo>`.
2. **Por quê** (parágrafo): o raciocínio/problema que motivou a mudança — não repetir o que
   o diff já mostra, explicar a razão por trás dele.
3. **O quê** (lista com marcadores, opcional): mudanças concretas relevantes, arquivo por
   arquivo ou tema por tema, quando o "por quê" sozinho não é suficiente para orientar quem
   revisa.
4. **Validação** (parágrafo, opcional): o que foi de fato testado/rodado nesta sessão de
   trabalho para confirmar que a mudança funciona.
5. **Referência cruzada** (linha final, opcional): se a mudança resolve uma decisão em
   aberto registrada em `plan.md`, ou fecha uma Issue, apontar para ela (ex.: "Resolves the
   hashing-algorithm open decision in specs/001-generate-api-key/plan.md", ou "Closes #12").

Exemplo:

```
feat: add HMAC-SHA256 hashing for generated API keys

Plaintext keys were being stored directly, which is a real risk if the
database is ever exposed — hashing means a leaked row is useless without
also compromising the hash and the separately-held pepper.

ApiKeyHasherTest proves the hash/verify round-trip and rejects a tampered
key (3/3 passing).

Resolves the hashing-algorithm open decision in specs/001-generate-api-key/plan.md.
Closes #4.
```

Commits pequenos e focados em uma mudança revisável de cada vez — evitar juntar mudanças sem
relação numa mesma mensagem.

### Enforcement

Um hook `commit-msg` versionado em `.githooks/commit-msg` valida o formato da primeira linha
automaticamente (o `<tipo>: ` do título — idioma não é validado por hook). Ativar uma vez por
clone/sessão:

```
git config core.hooksPath .githooks
```

## Branches e Pull Requests

- Um branch por linha de trabalho revisável — nome descritivo do que está sendo feito, não
  um identificador genérico.
- Nunca commitar direto no branch principal (`main`); toda mudança entra por PR.
- Uma PR corresponde a um branch — não empilhar trabalhos sem relação na mesma PR só porque
  foram feitos na mesma sessão.
- Título e descrição de PR seguem a mesma convenção de idioma das mensagens de commit —
  inglês — porque o GitHub usa o título da PR como corpo do merge commit em `main` quando a
  PR é mesclada; uma PR escrita em português vaza pro histórico de commits nesse ponto
  exatamente como um `git commit -m` em português vazaria.
- Quando o trabalho de uma PR já mesclada precisa continuar, reaproveitar o mesmo branch
  (recriado a partir do estado atual do branch principal) em vez de acumular branches novos a
  cada retomada — mantém o histórico de PRs correspondendo 1:1 a unidades de trabalho reais,
  não a sessões de chat.
- Essa regra não tem exceção por tipo de mudança nem por onde a sessão está rodando: uma
  continuação pequena, só de documentação, ou uma correção de CI ainda pertence à mesma linha
  de trabalho — não é motivo pra abrir um branch novo. E o mesmo vale trocando de ferramenta
  (Claude Code no navegador numa sessão, Claude Code local na outra): a continuidade é da
  linha de trabalho, não da sessão nem da ferramenta que a executa.
- Antes de criar um branch, checar se já existe um branch/PR abordando a mesma feature
  (`specs/NNN-*`) e reaproveitar esse em vez de abrir outro.

## Rastreamento de trabalho via Issues

O board de Issues do GitHub é o lugar para visualizar o andamento do projeto — ele espelha
`specs/*/tasks.md`, não duplica o controle manualmente:

| Nível SDD | GitHub |
|---|---|
| Uma feature (`specs/NNN-nome-da-feature/`) | 1 Issue "guarda-chuva" (épico), corpo linkando `spec.md` e `plan.md` |
| Cada tarefa de `tasks.md` (T001, T002...) | Item da checklist da Issue-épico, ou Issue própria quando a tarefa for grande o suficiente para ter discussão/PR isolada |
| Tipo do commit (`feat`/`fix`/`refactor`/...) | Label da Issue — mesma taxonomia da tabela de commits acima |
| Fase/marco do roadmap, quando existir | Milestone |

Commits e PRs fecham a Issue correspondente com `Closes #N` na mensagem — mesma convenção
usada para referenciar uma decisão resolvida em `plan.md`, só que apontando para a Issue.

Título e corpo da Issue em inglês (ver tabela "Idioma" acima) — mesmo raciocínio das
mensagens de commit e de PR: Issue, commit e PR compõem a mesma trilha rastreável, e misturar
idioma entre eles quebra essa continuidade.

## Documentação viva por feature

- Antes de implementar uma feature não trivial, escrever `specs/NNN-nome-da-feature/spec.md`
  (requisitos, critérios de aceite — o QUÊ e POR QUÊ, sem detalhes de implementação) e
  `plan.md` (decisões técnicas, arquitetura — o COMO). Funciona como uma ata que sobrevive a
  troca de contexto (nova sessão, outra pessoa assumindo o trabalho).
- Decisões em `plan.md` são marcadas como resolvidas no próprio texto conforme são tomadas,
  preservando o raciocínio e as alternativas consideradas — não só a conclusão final.
- Contratos de interface (`contracts/`, ex. comandos de CLI — args, exit codes, formato de
  saída) são escritos **antes** da implementação — a implementação segue o contrato.
- Modelo de dados (`data-model.md`), quando a feature envolver dados persistentes, é
  desenhado antes das entidades de código.
- Cenários de comportamento (Gherkin, quando usado) são escritos **antes** do código de
  implementação, dentro de `spec.md` ou como arquivos `.feature` referenciados por ela —
  funcionam como contrato de aceite, não como documentação a posteriori do que já foi
  construído.
- Cada feature nasce a partir de `templates/` (`spec-template.md`, `plan-template.md`,
  `tasks-template.md`, `data-model-template.md`, `contracts-template.md`) — copiar o
  template, não escrever do zero.

## Onde a documentação mora

Documentação de projeto tem plateias diferentes, e cada uma mora num lugar diferente:

- **`memory/constitution.md`** (este arquivo) — princípios e convenções, agnósticos de
  feature específica.
- **`specs/NNN-nome-da-feature/`** — a especificação viva de cada feature (`spec.md`,
  `plan.md`, `data-model.md`, `contracts/`, `tasks.md`). Serve quem está desenvolvendo essa
  feature.
- **`docs/`** — documentação de produto/arquitetura, destinada a quem avalia ou usa o
  projeto de fora (README, roadmap, diagramas). Pronta para ser publicada como está.
- **`docs/context/diario.md`** — diário de desenvolvimento: resumo do que foi feito a cada
  dia de trabalho, com os commits e Issues correspondentes (ver seção abaixo). Não é
  documentação de produto — não deve ser publicada junto com `docs/*.md`.

## Diário de desenvolvimento

`docs/context/diario.md` registra, por dia de trabalho, um resumo curto do que foi feito e a
lista de commits (hash curto + tipo + resumo) e Issues envolvidas naquele dia. Serve para
reconstruir "o que aconteceu e por quê" sem precisar reler o `git log` inteiro, e para dar
contexto a quem retoma o trabalho numa sessão nova.

Formato de cada entrada:

```markdown
## AAAA-MM-DD

**Resumo:** o que foi feito/decidido nesse dia, em 1-3 frases.

**Commits:**
- `<hash curto>` <tipo>: <resumo> (#<issue, se houver>)

**Issues:** #N aberta/fechada/em andamento
```

Uma entrada nova é adicionada ao final do arquivo a cada dia com commits relevantes — não
precisa ser todo dia se não houve trabalho.

## Diagramas Mermaid: validar a renderização de verdade

Um diagrama Mermaid com sintaxe que "parece certa" pode ainda assim falhar ao renderizar — a
gramática tem armadilhas que só aparecem no parser de verdade. Antes de considerar um
diagrama pronto, renderizar de verdade contra um motor Mermaid real (ex.:
`npx @mermaid-js/mermaid-cli`, mesmo motor que o GitHub usa para blocos ```` ```mermaid ````),
não só validar visualmente/mentalmente a sintaxe.

## Nomenclatura de ambientes

Nomear ambientes pela **característica real da infraestrutura**, não por um rótulo genérico
tipo "dev"/"qa"/"test" — esses termos são ambíguos e mudam de significado de projeto pra
projeto, obrigando a reexplicar o que cada um significa aqui. Preferir um nome que já
descreve a própria restrição ou característica do ambiente.

Exemplo usado neste projeto (`application-sandbox.yml`/`application-docker.yml`):

| Nome | O que descreve |
|---|---|
| `sandbox` | Ambiente sandbox da Claude: sem Docker, mas com PostgreSQL nativo provisionado pelo script de setup do próprio ambiente (fora do repositório). Mesmo banco real, sem substituto embarcado |
| `docker` | Infraestrutura real via containers, local (`docker-compose`) ou CI — descartável |
| `staging` | Pré-produção: infraestrutura e dados reais, mas isolados de produção |
| `production` | Produção |

Cada ambiente tem seu próprio arquivo de configuração autodescritivo — um comentário no topo
explicando por que aquele ambiente existe, quem o opera e quais restrições ele impõe.

## Testes: preferir real a fake sempre que der

Sempre que uma dependência externa tiver como rodar localmente/de verdade em teste
automatizado, preferir isso a um mock/stub. Um teste que passa contra uma simulação que não
bate com o comportamento real do sistema dá falsa confiança — "passou no teste, quebrou em
produção". Usar stub/fake só quando a alternativa real não existe ou não é viável no ambiente
de teste; mesmo nesses casos, o stub registra o que faria de verdade, para que o teste possa
checar por asserção em vez de só confiar que o método foi chamado.

## Dados de teste: Object Mother + Test Data Builder

Fábricas de dados de teste ("Mother") retornam um objeto/builder já pré-preenchido com dados
**válidos** por padrão — o ponto de partida de qualquer cenário. Cenários que testam uma
variação **inválida** de um campo específico partem desse builder válido e sobrescrevem só o
campo sob teste, mantendo os demais válidos.

## CI e cobertura de testes

- A suíte de testes roda em CI contra infraestrutura o mais real possível, não contra
  atalhos usados só no dia a dia local.
- Cobertura de linha tem um piso obrigatório, quando a stack tiver ferramenta de cobertura —
  não é só um número informativo, é uma condição de build passar. Código gerado fica de fora
  da contagem.
- O check de CI é obrigatório antes de mesclar (branch protection do GitHub no branch
  principal) — quebrar a suíte ou cair abaixo do piso de cobertura bloqueia o merge, não é um
  aviso.
- A cobertura aparece como comentário na própria PR, quando a ferramenta suportar, atualizado
  a cada push.

**Java** (stack deste projeto):

- Piso de cobertura via JaCoCo (`mvn verify`), 80% de linha.
- A suíte roda sempre contra PostgreSQL real, nunca contra um banco embarcado em memória: o
  `docker-compose.yml` localmente e no CI, ou o Postgres nativo no ambiente sandbox (ver
  "Nomenclatura de ambientes"). Sem Postgres de pé, os testes falham com erro de conexão, de
  propósito. CI (`.github/workflows/ci.yml`) sobe o serviço `db` e roda com
  `SPRING_PROFILES_ACTIVE=docker`.
- Como o banco local persiste entre execuções, todo teste que commita de verdade (fora de um
  `@Transactional` com rollback) apaga o que criou, e nenhum teste assume a tabela vazia.
- Cobertura comentada na PR a cada push via `madrapps/jacoco-report`.
