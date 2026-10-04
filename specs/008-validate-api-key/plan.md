# Plan: validate-api-key

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Diferente da CLI (processo de execução curta, uma invocação = um comando), esta biblioteca
roda dentro de um serviço Spring Boot já de pé, de longa duração — chamada a cada requisição
recebida por esse serviço. Reaproveita `ApiKey`, `ApiKeyRepository` e `ApiKeyHasher`, hoje
na package `.issuance` do único módulo Maven do projeto — que esta spec divide em módulos
(ver "Decisões de arquitetura" e "Estrutura de módulos/pacotes").

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| `ApiKeyFailureReason` carrega status HTTP e mensagem, como a proposta original sugeria? | Não — enum só com o código do motivo | resolvida | Status HTTP e texto de mensagem são decisões de apresentação/transporte de cada consumidor (idioma, formato de erro, se expõe `message` ao cliente externo). Embutir isso na biblioteca fixaria essa escolha pra todo mundo que a usar, inclusive um texto em português dentro de um tipo Java pensado pra ser reusado por qualquer serviço. |
| Checagem de formato (`MALFORMED`) | Regex exata contra o formato real de `ApiKeyGenerator`: prefixo `dak_` + 43 caracteres base64url sem padding — não a regex genérica `[A-Za-z0-9_-]{32,}` da proposta original | resolvida | A regex genérica nem checa o prefixo `dak_` nem o tamanho exato — mais frouxa que o necessário, deixaria passar pro hash+consulta strings que este projeto nunca poderia ter gerado. |
| Precedência quando uma chave está expirada E revogada | Revogada tem prioridade (`REVOKED` antes de `EXPIRED`) | resolvida | Mesma regra já usada e testada em `004-list-api-keys` FR1 — consistência entre as duas features que derivam status a partir dos mesmos dois campos (`revoked_at`, `expires_at`). |
| Nome do motivo para "hash não encontrado" | `NOT_FOUND`, não `INVALID` (como a proposta original) | resolvida | `ApiKeyValidationResult.Invalid` já é o nome do resultado-envelope; um motivo chamado `INVALID` dentro de um `Invalid` seria redundante e leria mal (`new Invalid(INVALID)`). `NOT_FOUND` descreve a causa real. |
| Nível de detalhe do motivo exposto externamente (genérico vs. específico) | A biblioteca sempre devolve o motivo exato; agrupar motivos (ex.: tratar `REVOKED`/`NOT_FOUND` como uma resposta genérica pro chamador externo) é decisão de quem consome | resolvida | Embutir essa política na biblioteca tira flexibilidade: uma API interna só pra clientes de confiança e uma API exposta publicamente têm necessidades diferentes, como a própria proposta original observou. A biblioteca só precisa garantir que a informação completa exista pra quem decide — ela não decide sozinha. |
| `RATE_LIMITED` como motivo de validação de chave | Fora de escopo — não entra no enum | resolvida | Limite de requisições não é uma propriedade da chave — é política de tráfego, tipicamente resolvida numa camada própria (filtro/proxy) antes ou depois da validação da chave. Colocar os dois no mesmo enum misturaria responsabilidades diferentes (validar identidade vs. conter volume). A menção na proposta original foi um comentário sem caso de uso concreto por trás. |
| Integração HTTP pronta (filtro Spring tipo `OncePerRequestFilter`, `@RestControllerAdvice`, formato de resposta JSON) | Fora da implementação; documentada como guia de integração para consumidores em [`http-integration.md`](http-integration.md), com as duas alternativas (filtro e interceptor + `@RestControllerAdvice`) | resolvida | Exigiria adicionar `spring-boot-starter-web`/servlet API como dependência da biblioteca — e sem um consumidor real integrando ainda (o serviço de e-mail do README ainda não existe neste repositório), testar um filtro de verdade teria pouco valor. O guia dá a quem integrar um ponto de partida sem amarrar a biblioteca a um stack web específico; vira código numa spec própria quando houver um consumidor real pra validar contra. |
| Módulo Maven separado (como `001-generate-api-key/plan.md` já previa: "devem ficar em pacotes/módulos Maven separados... para o serviço protegido não precisar trazer junto a lógica de geração/CLI") vs. mesmo artefato | Módulos Maven separados: `api-key-core`, `api-key-validation`, `api-key-cli` sob um POM agregador (ver "Estrutura de módulos/pacotes") | resolvida | Cumpre o que `001` já tinha decidido: o serviço protegido depende só de `api-key-validation` (+ `api-key-core`), sem trazer a CLI, os comandos de `management`, nem o Flyway. Fazer a divisão agora, enquanto há só um consumidor interno e nenhum externo, é mais barato do que depois de alguém já depender do artefato único. |

| Fonte do "agora" para revogação/expiração | `java.time.Clock` injetado no `ApiKeyValidator` (bean `Clock` do consumidor via `ObjectProvider`, com `Clock.systemUTC()` como fallback); `Instant.now()` direto não é usado | resolvida | Com `Instant.now()` não há como testar de forma determinística as bordas (chave que expira exatamente agora, revogação agendada que acabou de vencer) nem o cenário Gherkin de chave expirada sem manipular dados com datas relativas. Fallback em vez de registrar um bean `Clock` próprio para não conflitar com um `Clock` que o serviço consumidor já tenha. Os comandos da CLI que usam `Instant.now()` hoje ficam como estão — fora do escopo desta spec. |

Decisões marcadas "em aberto" bloqueiam a implementação (ver `tasks.md`, T000) — viram commit
`decision:` quando resolvidas, atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

O `pom.xml` atual vira um POM agregador (`packaging pom`, ainda filho de
`spring-boot-starter-parent`), com três módulos:

| Módulo | Conteúdo | Depende de |
|---|---|---|
| `api-key-core` | `ApiKey`, `ApiKeyRepository`, `ApiKeyHasher`, `MissingHmacPepperException` e o formato da chave (prefixo `dak_` + regex), movidos para a package `dev.leilaalgarve.apikey.core`; migrations Flyway (`db/migration`, `db/migration-h2`) como recursos | `spring-boot-starter-data-jpa` |
| `api-key-validation` | `ApiKeyFailureReason`, `ApiKeyValidationResult`, `ApiKeyValidator` (package `dev.leilaalgarve.apikey.validation`) | `api-key-core` |
| `api-key-cli` | Tudo o que existe hoje e não foi para o core: `DeployoApiKeyApplication`, `ApiKeyCliRunner`, `.issuance` (`GenerateCommand`, `ApiKeyGenerator`, `OldKeyRotationPolicy`), `.management`, `application*.yml`, Flyway, Postgres, testes Cucumber existentes | `api-key-core` |

Detalhes da divisão:

- **Formato da chave no core**: hoje o prefixo vive em `ApiKeyGenerator.PREFIX`
  (package-private, `.issuance`). Gerador e validador precisam concordar sobre o mesmo
  formato, então ele sobe para uma classe `ApiKeyFormat` no core (prefixo, bytes de entropia,
  regex) — `ApiKeyGenerator` passa a usá-la, sem mudar a chave gerada.
- **Migrations no core, Flyway só na CLI**: o schema pertence à entidade que o mapeia
  (`ApiKey`), então os scripts ficam no core. O core não depende de Flyway — quem roda as
  migrations é a CLI (como hoje). Um consumidor que só valide recebe os scripts no classpath
  mas não os executa, a menos que inclua Flyway por conta própria. Os testes de
  `api-key-validation` usam Flyway + H2 como dependência de teste para ter o schema real.
- **`spring-boot-maven-plugin` só em `api-key-cli`**: o `repackage` transforma o jar num fat
  jar executável, que não serve como dependência — aplicado no core/validation, quebraria o
  consumo como biblioteca. O artefato executável continua sendo um só (agora
  `api-key-cli/target/api-key-cli-<versão>.jar`).
- **Cobertura**: o gate de 80% do JaCoCo passa a valer por módulo. O passo
  `madrapps/jacoco-report` do CI aponta hoje para `target/site/jacoco/jacoco.xml` — passa a
  listar o relatório de cada módulo.
- **Renomeação de package das classes movidas**: `ApiKey`/`ApiKeyRepository`/`ApiKeyHasher`/
  `MissingHmacPepperException` saem de `.issuance` para `.core` — commit `refactor:` próprio,
  sem mudança de comportamento, antes de qualquer código novo de validação (mesmo racional de
  `005-refactor-pacote-base`).

Novas classes em `dev.leilaalgarve.apikey.validation`:

- `ApiKeyFailureReason` (enum)
- `ApiKeyValidationResult` (`sealed interface` + `record`s `Valid`/`Invalid`)
- `ApiKeyValidator` (`@Component`, orquestra FR4)

## Riscos e trade-offs

- **Enumeração de chaves**: devolver o motivo exato (`REVOKED` vs. `NOT_FOUND` vs. `EXPIRED`)
  permite a quem tem acesso às respostas do consumidor inferir se uma chave específica já
  existiu um dia. Mitigação é responsabilidade de quem consome a biblioteca (ver decisão de
  "nível de detalhe" acima) — o Javadoc de `ApiKeyFailureReason`/`ApiKeyValidationResult` e
  o guia [`http-integration.md`](http-integration.md) deixam isso explícito, pra quem for
  integrar não ser pego de surpresa.
- **Refactor multi-módulo antes da feature**: mover classes de package e dividir o build é
  uma mudança grande, sem valor visível sozinha, e toca CI, README (caminho do jar) e todos os
  testes existentes. Aceito porque já era a intenção desde `001` e fica mais caro a cada
  feature nova no módulo único; mitigado fazendo a divisão em commits `refactor:` próprios,
  com `mvn clean verify` verde e o mesmo número de testes antes de começar a validação.
- **Migrations no classpath do consumidor**: um serviço que use Flyway para o próprio schema
  e dependa de `api-key-core` passaria a enxergar `db/migration` deste projeto também.
  Aceito por ora (o consumidor previsto roda no mesmo banco, cujas migrations são estas);
  revisar se aparecer um consumidor com schema próprio.
  **Atualização (1.0.1):** apareceu um consumidor com migrations próprias em `db/migration` e
  houve colisão; os scripts foram para `db/migration-api-key`, que só a CLI (e os testes)
  apontam em `spring.flyway.locations`.
