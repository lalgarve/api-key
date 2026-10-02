# Tasks: Subir o Adminer via Docker para inspeção manual do banco

Quebra `spec.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Esta spec não tem
`plan.md` — não há decisão técnica em aberto para uma mudança puramente de infraestrutura de
apoio a teste manual, mesmo racional já usado em `005-refactor-pacote-base`.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Acrescentar o serviço `adminer` (imagem `adminer`) a `docker-compose.yml`, apontando para o serviço `db` já existente, atrás de um `profiles` do Docker Compose (não sobe num `docker compose up -d` comum, nem no `docker compose up -d --wait db` já usado pelo CI) | — | | #21 |
| ~~T002~~ | Documentar no `README.md` o comando para subir o Adminer (`docker compose up -d adminer`) e os dados de conexão a preencher na tela de login (sistema `PostgreSQL`, servidor = nome do serviço `db`, usuário/senha/banco = os já definidos em `docker-compose.yml`) | T001 | | #21 |
| ~~T003~~ | Validar manualmente: `docker compose up -d db adminer`, abrir o Adminer no navegador, logar com os dados documentados, confirmar que as tabelas das migrations Flyway aparecem | T001, T002 | | #21 |
| ~~T004~~ | Confirmar que o CI continua subindo só `db` (não `adminer`) — reler `.github/workflows/ci.yml`, sem precisar alterá-lo | T001 | [P] | #21 |
| ~~T005~~ | Registrar a mudança numa entrada nova de `docs/context/diario.md` (não editar entradas antigas) | T003, T004 | [P] | #21 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Todas as tarefas desta feature estão concluídas.
