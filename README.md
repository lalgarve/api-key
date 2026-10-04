# api-key

Biblioteca e aplicação Java para gerar API-KEY pela linha de comando.

## Contexto e propósito

Este projeto nasce dentro do ecossistema do [`jogo-acoes`](https://github.com/lalgarve/jogo-acoes),
que hoje autentica usuários por link mágico enviado por e-mail. O envio de e-mail está sendo
extraído para um serviço próprio — e esse serviço precisa de uma forma de restringir quem
pode chamá-lo. A solução escolhida é autenticação via API-KEY.

Em vez de resolver isso só para o serviço de e-mail, o objetivo é ter um padrão que qualquer
serviço interno possa adotar para restringir quem pode chamá-lo: cada serviço protegido roda
sua própria cópia deste projeto — mesmo container, banco de dados próprio, nunca compartilhado
entre serviços diferentes — e usa o comando `generate` para emitir uma chave por **cliente**
autorizado (ex.: `jogo-acoes` chamando o serviço de e-mail; se amanhã existir um serviço de
cobrança, ele teria sua própria instância e seus próprios clientes). Como o número de clientes
que vão precisar de chave é pequeno por enquanto (1, o próprio `jogo-acoes`), uma interface de
administração foi conscientemente deixada de fora do escopo inicial: a linha de comando já
resolve o problema real sem o custo de construir e manter uma UI que ninguém usaria ainda.

Por ser um projeto pequeno e autocontido, ele também serve como **exemplo compacto de
Spec-Driven Development (SDD)** — a metodologia usada aqui, mais fácil de avaliar de ponta a
ponta do que o `jogo-acoes` (que já é grande e usa uma abordagem correlata, BDD + DER + OpenAPI
escritos antes da implementação, mas não formalizada como SDD). A intenção é usar este
repositório para pedir ao professor da disciplina autorização para adotar SDD como
metodologia — ver o [documento de alinhamento do jogo-acoes](https://github.com/lalgarve/jogo-acoes/blob/docs/alinhamento-projeto-disciplina/docs/context/alinhamento-projeto-disciplina.md)
para o contexto acadêmico completo.

## Uso

```
export API_KEY_HMAC_PEPPER=<segredo-do-ambiente>
java -jar api-key.jar generate --client jogo-acoes [--validity-days 90] [--revoke-old-in-days 7]
java -jar api-key.jar revoke --id 3 [--in-days 14]
java -jar api-key.jar list [--status active|expired|revoked|all] [--client jogo-acoes] [--revoking-within-days 30]
```

A chave em texto puro é impressa **uma única vez**, na hora da geração — guarde-a
imediatamente, não há como recuperá-la depois. `revoke` identifica a chave pelo seu `id`
numérico (nunca pela chave em texto puro nem pelo hash) — descubra o `id` com `list`. Sem
`--in-days`, a revogação é imediata; com `--in-days`, fica agendada para aquele número de dias
a partir de agora, sem nunca passar do prazo de validade (`--validity-days`) já definido para a
chave.

Rotação de chave: `generate --client jogo-acoes --revoke-old-in-days 7` gera a chave nova **e**
agenda a revogação de qualquer chave já ativa do mesmo cliente para 7 dias depois — `0` revoga
na hora (corte imediato). Sem `--revoke-old-in-days`, `generate` nunca toca em nenhuma chave
existente, mesmo que o cliente já tenha uma ativa.

`list` mostra, por padrão, só chaves ativas; `--status all/expired/revoked` muda o filtro, e
`--revoking-within-days N` (só combinável com `--status active`, padrão ou explícito) mostra só
chaves com revogação agendada para os próximos N dias. O status de cada linha é derivado na
hora da consulta (nunca armazenado) — `revoked` tem prioridade sobre `expired`, que tem
prioridade sobre `active`.

Ver [`specs/001-generate-api-key/contracts/cli-commands.md`](specs/001-generate-api-key/contracts/cli-commands.md)
(estendido por [`specs/003-auto-revoke-on-rotation/contracts/cli-commands.md`](specs/003-auto-revoke-on-rotation/contracts/cli-commands.md)),
[`specs/002-revoke-api-key/contracts/cli-commands.md`](specs/002-revoke-api-key/contracts/cli-commands.md)
e [`specs/004-list-api-keys/contracts/cli-commands.md`](specs/004-list-api-keys/contracts/cli-commands.md)
para os contratos completos (argumentos, saída, exit codes) de cada comando.

## Validando uma API-KEY no serviço protegido

O projeto é multi-módulo Maven:

| Módulo | Para quê |
|---|---|
| `api-key-core` | Entidade, repositório, hash e formato da chave, migrations do schema |
| `api-key-validation` | Biblioteca que o serviço protegido usa para validar a chave recebida |
| `api-key-cli` | A CLI acima (`mvn package` gera `api-key-cli/target/api-key-<versão>.jar`) |

O serviço protegido depende só de `api-key-validation` — sem trazer a CLI nem o Flyway — e
chama `ApiKeyValidator.validate(chaveRecebida)`, que devolve um resultado tipado:

```java
switch (validator.validate(request.getHeader("X-API-Key"))) {
    case ApiKeyValidationResult.Valid valid -> {
        // valid.clientName() é o --client usado ao gerar a chave
    }
    case ApiKeyValidationResult.Invalid invalid -> {
        // invalid.reason(): MISSING, MALFORMED, NOT_FOUND, EXPIRED ou REVOKED
    }
}
```

Ele precisa do mesmo banco e do mesmo `API_KEY_HMAC_PEPPER` desta instância. O que responder
ao chamador para cada motivo fica com o serviço — ver o
[guia de integração HTTP](specs/008-validate-api-key/http-integration.md) (filtro ou
interceptor + `@RestControllerAdvice`) e o
[contrato da biblioteca](specs/008-validate-api-key/contracts/validation-api.md).

## Operação: backup do pepper do HMAC

O hash de cada chave é calculado com HMAC-SHA256 usando um pepper lido da variável de
ambiente `API_KEY_HMAC_PEPPER` — mantido fora do banco de dados e do código-fonte de
propósito (ver `specs/001-generate-api-key/plan.md`, "Onde fica o pepper do HMAC").

**Perder o pepper é irreversível.** Sem ele, nenhum hash já persistido pode ser recalculado
para validação — é equivalente a perder todas as chaves já emitidas; cada cliente precisaria
receber uma chave nova. Trate o valor do pepper como um segredo crítico:

- Guarde-o no gerenciador de segredos do ambiente onde este serviço roda (nunca em
  repositório de código, nem em log).
- Faça backup do pepper junto com — mas separado do — backup do banco de dados: os dois
  juntos permitem restaurar a capacidade de validar chaves; o banco sozinho não.
- Ao rotacionar o pepper deliberadamente, todas as chaves já emitidas deixam de validar —
  equivalente a revogar todas de uma vez. Não há suporte (ainda) a múltiplos peppers válidos
  simultaneamente para uma rotação gradual.

## Operação: inspecionar o banco manualmente com Adminer

Para conferir manualmente, direto no banco, o efeito de um comando da CLI rodado contra o
perfil `docker` (ex.: depois de um `revoke`/`generate`, ver as linhas da tabela `api_key`), o
`docker-compose.yml` deste projeto tem um serviço opcional do [Adminer](https://www.adminer.org/)
— cliente de banco de dados via navegador, sem precisar instalar nada além do que o projeto já
usa (Docker). Ele fica atrás de um profile do Docker Compose, então **não** sobe junto com um
`docker compose up -d` comum nem com o que o CI já usa — só quando pedido explicitamente:

```
docker compose up -d db adminer
```

Depois, abra `http://localhost:8080` no navegador e preencha a tela de login do Adminer com os
mesmos valores já definidos em `docker-compose.yml` para o serviço `db`:

| Campo | Valor |
|---|---|
| Sistema | PostgreSQL |
| Servidor | `db` |
| Usuário | `deployo_api_key_admin` |
| Senha | `deployo_api_key_admin` |
| Banco de dados | `deployo_api_key` |

Para encerrar, `docker compose down` (ou `docker compose stop adminer` para só parar o
Adminer, mantendo o banco no ar).

## Rodando os testes

A suíte roda contra PostgreSQL real, nunca contra um banco em memória (ver
`specs/009-remove-h2-real-postgres`). Suba o banco antes:

```
docker compose up -d --wait db
mvn verify
```

Sem o Postgres de pé, os testes falham com erro de conexão, de propósito. Para apontar para
outro host ou porta (ex.: a 5432 já ocupada por outro projeto), use as variáveis
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. No
ambiente sandbox da Claude, que não tem Docker, os testes e o perfil `sandbox` usam o Postgres
nativo do ambiente, com o mesmo endereço e as mesmas credenciais.

### Testes caixa-preta da CLI (sob demanda)

Uma segunda suíte, em Python com BDD, roda o jar empacotado como processo separado e confere só
exit code, stdout e stderr. Ela não faz parte do `mvn verify` nem do CI obrigatório: roda quando
pedida, localmente ou pelo workflow manual "Black-box CLI tests" no GitHub Actions.

```
mvn package -DskipTests
blackbox-tests/run.sh
```

Ver [`blackbox-tests/README.md`](blackbox-tests/README.md) e
[`specs/012-blackbox-cli-tests/`](specs/012-blackbox-cli-tests/).

## Releases

A versão vem do `pom.xml` raiz (e do `<parent>` de cada módulo). Para publicar uma release:

1. Por PR, trocar a versão nos quatro `pom.xml` para a versão final (sem `-SNAPSHOT`) e levar
   isso até o `main`.
2. No GitHub Actions, rodar o workflow manual "Release" sobre o `main`. Ele recusa versão
   `-SNAPSHOT` ou tag já existente, roda o mesmo `mvn verify` do CI contra Postgres real e só
   então publica `api-key-core` e `api-key-validation` no GitHub Packages e cria a tag
   `v<versão>` e a release no GitHub, com o jar da CLI (`api-key-<versão>.jar`) e os jars das
   duas bibliotecas anexados, mais as notas geradas a partir das PRs.

### Usar a biblioteca num serviço

O serviço protegido declara o repositório do GitHub Packages e a dependência:

```xml
<repositories>
  <repository>
    <id>github-api-key</id>
    <url>https://maven.pkg.github.com/lalgarve/api-key</url>
  </repository>
</repositories>

<dependency>
  <groupId>dev.leilaalgarve.apikey</groupId>
  <artifactId>api-key-validation</artifactId>
  <version>1.0.1</version>
</dependency>
```

O GitHub Packages exige autenticação até para ler: no `~/.m2/settings.xml` (ou no CI do
serviço), um `<server>` com o mesmo `id` (`github-api-key`), o usuário do GitHub e um token
com `read:packages`.

O serviço sobe pela própria classe Spring Boot e só valida chaves. Gerar, revogar e listar
continua sendo o jar da CLI, rodado à parte contra o mesmo banco.

### Migrations e banco compartilhado

Os scripts ficam em `db/migration-api-key` e o histórico do Flyway da CLI na tabela
`api_key_schema_history`, para não colidir com um serviço que use o Flyway no mesmo banco
(`db/migration` e `flyway_schema_history` são os padrões dele). A CLI faz baseline na versão 0
quando o schema já tem tabelas do serviço, e aplica V1 e V2 por cima.

Um banco criado pela 1.0.0 tem o histórico em `flyway_schema_history`. Antes de rodar a 1.0.1
nele, renomear a tabela uma vez:

```sql
ALTER TABLE flyway_schema_history RENAME TO api_key_schema_history;
```

Cada release publicada fica em [Releases](https://github.com/lalgarve/api-key/releases).

## Metodologia de desenvolvimento

Este projeto usa Spec-Driven Development (SDD):

- [`memory/constitution.md`](memory/constitution.md) — convenções do projeto (idioma,
  commits, branches/PR, testes, rastreamento de trabalho via Issues).
- [`specs/`](specs/) — spec, plano técnico e tarefas de cada feature, escritos antes da
  implementação.
- [`templates/`](templates/) — modelos usados para começar uma feature nova.

Os cenários Gherkin de cada spec são executados de verdade por `mvn verify`, não só
documentados: `src/test/resources/features/` de cada módulo Maven (Cucumber) roda junto com a suíte JUnit, cada
`.feature` é o contrato de aceite da spec correspondente (ver
`specs/006-executable-gherkin-scenarios`).

## Uso de ferramentas de IA

Conforme a política "Sinal Verde" da disciplina (mesma adotada no `jogo-acoes`), o
desenvolvimento deste projeto conta com apoio de ferramentas de IA (Claude Code, Anthropic)
— incluindo a própria definição da estrutura de SDD usada aqui. Todo conteúdo gerado ou
revisado com apoio de IA é lido, entendido e validado antes de ser incorporado ao
repositório; commits e decisões de arquitetura permanecem de responsabilidade da autora.
