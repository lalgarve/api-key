# Spec: Subir o Adminer via Docker para inspeção manual do banco

**Status:** aprovada — implementada (T001-T005 concluídas)
**Issue:** #21

## Resumo

Acrescentar a `docker-compose.yml` um serviço opcional do [Adminer](https://www.adminer.org/)
(cliente de banco de dados via navegador), para inspecionar manualmente o PostgreSQL usado
pelo perfil `docker` durante testes manuais — sem precisar de um cliente SQL instalado
localmente. Documentar no `README.md` como subir esse serviço e quais dados preencher na tela
de login do Adminer.

## Motivação

Hoje, confirmar manualmente o efeito de um comando da CLI direto no banco (ex.: conferir que
`revoke`/`list` refletem o estado esperado após rodar contra o perfil `docker`) exige um
cliente PostgreSQL instalado à parte (`psql`, DBeaver etc.) apontando para a porta já exposta
em `docker-compose.yml`. Um serviço Adminer no próprio `docker-compose.yml` remove essa
dependência externa — basta um navegador, sem instalar nada além do que o projeto já usa
(Docker).

## Cenários (comportamento esperado)

Não aplicável — serviço de infraestrutura/ferramenta de apoio a teste manual, sem
comportamento de aplicação Java nem de CLI a especificar. Critério de aceite: subir `db` e
`adminer` via `docker compose`, abrir o Adminer no navegador, logar com os dados documentados
no `README.md`, e ver as tabelas já criadas pelas migrations Flyway deste projeto.

## Requisitos funcionais

- FR1: `docker-compose.yml` ganha um serviço `adminer` (imagem oficial `adminer`), configurado
  para conectar no serviço `db` já existente no mesmo `docker-compose.yml`.
- FR2: O serviço `adminer` é opcional — não sobe junto com um `docker compose up -d` comum,
  nem passa a ser iniciado pelo CI (que já sobe só o serviço `db`, explicitamente, em
  `.github/workflows/ci.yml`). Só sobe quando pedido explicitamente por nome
  (`docker compose up -d adminer`).
- FR3: `README.md` documenta o comando para subir o Adminer e os dados de conexão a preencher
  na tela de login (sistema, servidor, usuário, senha, banco) — reaproveitando os mesmos
  valores já definidos em `docker-compose.yml` para o serviço `db`, sem duplicar/hardcodar um
  segundo conjunto de credenciais.

## Requisitos não-funcionais

- Não altera nada do comportamento da aplicação Java, da CLI, nem do pipeline de CI (que
  continua subindo só `db`, nunca `adminer`).

## Fora de escopo

- Autenticação/hardening do Adminer (ex.: senha própria do Adminer, HTTPS, restrição de rede)
  — ferramenta de uso local e manual, nunca exposta em nenhum ambiente real.
- Qualquer mudança em `application-docker.yml` ou nas credenciais já existentes do banco.
- Subir o Adminer em CI ou em qualquer ambiente além do `docker-compose.yml` local.

## Decisões em aberto

Nenhuma — mudança pequena e mecânica o suficiente para não precisar de `plan.md` (mesmo
racional usado em `005-refactor-pacote-base`): o mecanismo de opt-in (profile do Docker
Compose) e a porta do Adminer ficam registrados diretamente em `tasks.md`/na implementação.
