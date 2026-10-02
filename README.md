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
