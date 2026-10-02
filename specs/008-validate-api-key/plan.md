# Plan: validate-api-key

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Diferente da CLI (processo de execução curta, uma invocação = um comando), esta biblioteca
roda dentro de um serviço Spring Boot já de pé, de longa duração — chamada a cada requisição
recebida por esse serviço. Reaproveita `ApiKey`, `ApiKeyRepository` e `ApiKeyHasher`, já
existentes na package `.issuance`, no mesmo módulo/JAR/banco desta mesma instância.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| `ApiKeyFailureReason` carrega status HTTP e mensagem, como a proposta original sugeria? | Não — enum só com o código do motivo | resolvida | Status HTTP e texto de mensagem são decisões de apresentação/transporte de cada consumidor (idioma, formato de erro, se expõe `message` ao cliente externo). Embutir isso na biblioteca fixaria essa escolha pra todo mundo que a usar, inclusive um texto em português dentro de um tipo Java pensado pra ser reusado por qualquer serviço. |
| Checagem de formato (`MALFORMED`) | Regex exata contra o formato real de `ApiKeyGenerator`: prefixo `dak_` + 43 caracteres base64url sem padding — não a regex genérica `[A-Za-z0-9_-]{32,}` da proposta original | resolvida | A regex genérica nem checa o prefixo `dak_` nem o tamanho exato — mais frouxa que o necessário, deixaria passar pro hash+consulta strings que este projeto nunca poderia ter gerado. |
| Precedência quando uma chave está expirada E revogada | Revogada tem prioridade (`REVOKED` antes de `EXPIRED`) | resolvida | Mesma regra já usada e testada em `004-list-api-keys` FR1 — consistência entre as duas features que derivam status a partir dos mesmos dois campos (`revoked_at`, `expires_at`). |
| Nome do motivo para "hash não encontrado" | `NOT_FOUND`, não `INVALID` (como a proposta original) | resolvida | `ApiKeyValidationResult.Invalid` já é o nome do resultado-envelope; um motivo chamado `INVALID` dentro de um `Invalid` seria redundante e leria mal (`new Invalid(INVALID)`). `NOT_FOUND` descreve a causa real. |
| Nível de detalhe do motivo exposto externamente (genérico vs. específico) | A biblioteca sempre devolve o motivo exato; agrupar motivos (ex.: tratar `REVOKED`/`NOT_FOUND` como uma resposta genérica pro chamador externo) é decisão de quem consome | resolvida | Embutir essa política na biblioteca tira flexibilidade: uma API interna só pra clientes de confiança e uma API exposta publicamente têm necessidades diferentes, como a própria proposta original observou. A biblioteca só precisa garantir que a informação completa exista pra quem decide — ela não decide sozinha. |
| `RATE_LIMITED` como motivo de validação de chave | Fora de escopo desta spec | **em aberto** | Limite de requisições não é uma propriedade da chave — é política de tráfego, tipicamente resolvida numa camada própria (filtro/proxy) antes ou depois da validação da chave. Colocar os dois no mesmo enum misturaria responsabilidades diferentes (validar identidade vs. conter volume). Mas a proposta original incluía explicitamente — confirmar se topam deixar fora por ora ou se há um motivo concreto para incluir já. |
| Integração HTTP pronta (filtro Spring tipo `OncePerRequestFilter`, formato de resposta JSON) | Fora de escopo desta spec | **em aberto** | Exigiria adicionar `spring-boot-starter-web`/servlet API como dependência deste artefato — hoje a aplicação é `web-application-type: none`, sem essa dependência (ver `application.yml`). Sem um consumidor real integrando ainda (o serviço de e-mail do README ainda não existe neste repositório), testar um filtro de verdade teria pouco valor — melhor como spec separada quando houver um consumidor real pra validar contra. Confirmar se topam deixar fora por ora. |
| Módulo Maven separado (como `001-generate-api-key/plan.md` já previa: "devem ficar em pacotes/módulos Maven separados... para o serviço protegido não precisar trazer junto a lógica de geração/CLI") vs. mesmo artefato | Mesmo artefato, nova package `dev.leilaalgarve.apikey.validation` | **em aberto** | Mais simples — consistente com `.issuance`/`.management` já coexistindo numa package só, sem módulo Maven separado, sem problema prático até hoje. Custo de um módulo Maven de verdade (pom pai + reatores, versionamento próprio do artefato de leitura) só se paga quando existir um segundo projeto real consumindo só a validação; `001`'s `plan.md` já antecipava a divisão, mas o projeto ainda não tem esse segundo consumidor. Confirmar se aceitam adiar a divisão em módulo, ou se preferem já separar agora. |

Decisões marcadas "em aberto" bloqueiam a implementação (ver `tasks.md`, T000) — viram commit
`decision:` quando resolvidas, atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

Nova package `dev.leilaalgarve.apikey.validation` (paralela a `.issuance` e `.management`),
reaproveitando sem alteração de comportamento:

- `dev.leilaalgarve.apikey.issuance.ApiKey` — `isRevoked(Instant)`/`isExpired(Instant)` já
  existem e já são usados por `004-list-api-keys`.
- `dev.leilaalgarve.apikey.issuance.ApiKeyHasher` — mesmo HMAC-SHA256 com pepper de `generate`.
- `dev.leilaalgarve.apikey.issuance.ApiKeyRepository` — ganha `findByKeyHash` (FR5 de
  `spec.md`), usado tanto por esta feature quanto, potencialmente, por futuras.

Novas classes nesta package:

- `ApiKeyFailureReason` (enum)
- `ApiKeyValidationResult` (`sealed interface` + `record`s `Valid`/`Invalid`)
- `ApiKeyValidator` (`@Component`, orquestra FR4)

## Riscos e trade-offs

- **Enumeração de chaves**: devolver o motivo exato (`REVOKED` vs. `NOT_FOUND` vs. `EXPIRED`)
  permite a quem tem acesso às respostas do consumidor inferir se uma chave específica já
  existiu um dia. Mitigação é responsabilidade de quem consome a biblioteca (ver decisão de
  "nível de detalhe" acima) — o Javadoc de `ApiKeyFailureReason`/`ApiKeyValidationResult` deixa
  isso explícito, pra quem for integrar não ser pego de surpresa.
- **Mesmo módulo Maven, por ora**: um futuro serviço consumidor que só precise da validação
  ainda importaria transitivamente Flyway/CLI/`management` deste artefato. Aceito
  conscientemente enquanto não existir um consumidor real — revisar se isso incomodar na
  prática (ver decisão acima).
