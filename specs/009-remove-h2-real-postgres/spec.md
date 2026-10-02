# Spec: H2 sai do projeto — Postgres real sempre (testes, sandbox e Docker)

**Status:** rascunho
**Issue:** #<a criar>

## Resumo

H2 sai do projeto inteiro: da suíte de testes automatizados (`mvn test`/`mvn verify`) dos três
módulos (`api-key-core`, `api-key-validation`, `api-key-cli`) e do perfil `sandbox` da CLI. Os
dois passam a usar PostgreSQL real. A suíte usa o Postgres que já está de pé via
`docker-compose.yml` (serviço `db`). O perfil `sandbox` usa um Postgres já disponível no
ambiente, nativo ou via compose (ver "Decisões em aberto"). Se não houver Postgres de pé, os
testes falham alto, com erro de conexão. Não existe fallback silencioso para um substituto mais
fraco.

Adaptada da spec `05-028-testes-exigem-docker-real` do projeto `jogo-acoes`, que tomou a mesma
decisão pelo mesmo motivo. Lá a spec também removia Testcontainers/LocalStack. Aqui não há nenhum
dos dois: o escopo é só H2.

## Motivação

1. **O padrão de `mvn test` é H2, e Postgres só entra se alguém lembrar de setar
   `SPRING_PROFILES_ACTIVE=docker`.** Os três `src/test/resources/application.yml` apontam para
   `jdbc:h2:mem:...;MODE=PostgreSQL` com `db/migration-h2`. O CI já faz certo: sobe `db` e roda
   com `SPRING_PROFILES_ACTIVE=docker`. Mas qualquer execução local sem essa variável cai em H2
   sem avisar. Isso contradiz "Testes: preferir real a fake sempre que der" em
   `memory/constitution.md`. `MODE=PostgreSQL` é uma aproximação: o H2 aceita coisas que o
   Postgres rejeita, e o contrário também acontece.
2. **H2 obriga a manter duas árvores de migration em paralelo.** `api-key-core` tem
   `db/migration` (Postgres) e `db/migration-h2` (sabor H2), com o mesmo conteúdo lógico escrito
   duas vezes. Cada mudança de schema precisa ser escrita e revisada em dobro, e a versão H2 nunca
   roda em produção.
3. **A configuração ficou cheia de remendos para o H2 e o Postgres conviverem.** Exemplos:
   `driver-class-name` e `flyway.locations` explícitos em todo `application-docker.yml` porque o
   `application.yml` de teste fixa os de H2, e `DeployoApiKeyApplicationTests` sobrescrevendo URL e
   driver porque o perfil `docker` ganhava. Esse tipo de interação só aparece no CI. Com um banco
   só, os remendos somem.
4. **O motivo original do H2 no `sandbox` ("No Docker/Postgres available here") deixou de valer
   no `jogo-acoes`.** O ambiente sandbox de lá ganhou Postgres nativo. Se o mesmo valer aqui (ver
   "Decisões em aberto"), não sobra razão para manter H2 em lugar nenhum.

## Cenários (comportamento esperado)

Esta spec não adiciona nem muda comportamento de produto. É infraestrutura de teste e de
ambiente, e não há `.feature` novo. O comportamento esperado é sobre a suíte e os perfis:

- `mvn test`/`mvn verify` (raiz ou qualquer módulo), rodado sem nenhuma variável de ambiente
  especial, conecta em PostgreSQL real (`localhost:5432/deployo_api_key`, as mesmas credenciais de
  `application-docker.yml`), nunca em H2.
- Se esse Postgres não estiver de pé (`docker compose up -d --wait db` não executado antes), os
  testes falham de forma clara e imediata com erro de conexão. Não há fallback para H2 nem skip
  silencioso.
- Rodar `mvn verify` duas vezes seguidas contra o mesmo Postgres, sem `docker compose down -v`
  entre as rodadas, passa nas duas. Isso prova que a suíte não depende de começar com o banco
  vazio, como acontecia com H2 em memória.
- O CI continua verde, com o mesmo `docker compose up -d --wait db` de hoje.
- `SPRING_PROFILES_ACTIVE=sandbox` (o padrão da CLI) sobe contra PostgreSQL real e aplica
  `db/migration`.

## Requisitos funcionais

- Remover H2 de `src/test/resources/application.yml` nos três módulos. Eles passam a apontar
  direto para o Postgres real (URL, usuário, senha e driver de `application-docker.yml`, com
  `db/migration`), sem depender de profile ou variável de ambiente.
- Remover a dependência `com.h2database:h2` dos três `pom.xml`.
- Remover `api-key-core/src/main/resources/db/migration-h2/`.
- `application-sandbox.yml` (CLI) troca H2 por PostgreSQL real com `db/migration`.
- Testes que hoje forçam uma URL `jdbc:h2:...` (`DeployoApiKeyApplicationTests`,
  `ApiKeyCliRunnerTest`) deixam de depender de H2.
- `docker-compose.yml` continua sendo a única fonte da infraestrutura usada em teste. Nenhum
  serviço novo e nada subido por fora dele (sem Testcontainers).
- Comentários e documentação que descrevem o `sandbox` como "H2 embarcado" são atualizados
  (`memory/constitution.md` "Nomenclatura de ambientes" e "CI e cobertura de testes", README,
  comentários nos YAML e nos testes).

## Requisitos não-funcionais

- Nenhum serviço novo no `docker-compose.yml`: o `db` que o CI já sobe basta.
- O piso de cobertura (JaCoCo, 80%) continua valendo e passando.

## Fora de escopo

- Testcontainers e LocalStack. Não existem neste projeto, e a spec não os introduz.
- Provisionar Postgres nativo no ambiente sandbox (script de setup, clusters). Se existir, fica
  fora deste repositório. Esta spec só aponta a configuração para ele.
- Isolamento entre execuções concorrentes da suíte no mesmo Postgres (ex.: duas sessões rodando
  `mvn verify` ao mesmo tempo). O alvo é a repetibilidade sequencial.
- Qualquer mudança de comportamento de produção de `generate`/`revoke`/`list`/validação.

## Decisões em aberto

- **Qual Postgres o perfil `sandbox` usa?** No `jogo-acoes`, o ambiente sandbox da Claude tem
  Postgres nativo provisionado fora do repositório, mas com os bancos e roles daquele projeto.
  Falta confirmar se o mesmo ambiente provisiona (ou pode provisionar) `deployo_api_key` com o
  role `deployo_api_key_admin`. Também é preciso escolher a porta: no `jogo-acoes` a 5432 é do
  `jogo_acoes`. Se não houver Postgres nativo, as alternativas são o `sandbox` passar a exigir
  `docker compose up db` (e aí fica igual ao `docker`, o que questiona a existência do perfil) ou
  o perfil ser removido.
