# Tasks: generate-api-key

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T000~~ | Resolver as decisões em aberto de `plan.md` — resolvida: Spring Data JPA/Hibernate, PostgreSQL (`docker`/CI) + H2 (`sandbox`), Flyway | — | | #2 |
| ~~T001~~ | Criar migration Flyway da tabela `api_keys` (`data-model.md`, incluindo `expires_at`) | T000 | | #2 |
| ~~T002~~ | Implementar geração de chave aleatória com prefixo `dak_` (32 bytes de entropia, base64url) | — | [P] | #2 |
| ~~T003~~ | Implementar hashing HMAC-SHA256 lendo o pepper de variável de ambiente, com erro claro se ausente | — | [P] | #2 |
| ~~T004~~ | Implementar parsing/validação de `--validity-days` (inteiro positivo opcional) e cálculo de `expires_at` (nulo quando omitido) | — | [P] | #2 |
| ~~T005~~ | Implementar persistência via Spring Data JPA (entidade `ApiKey`, repositório) — grava `client_name`, `key_hash`, `created_at`, `expires_at` | T001 | | #2 |
| ~~T006~~ | Implementar o comando `generate` da CLI (parsing de `--client`/`--validity-days`, orquestração geração → hash → validade → persistência → impressão única da chave), seguindo `contracts/cli-commands.md` | T002, T003, T004, T005 | | #2 |
| ~~T007~~ | Testes: cenários de sucesso (com e sem `--validity-days`); `--client` ausente/vazio; `--validity-days` inválido; pepper ausente; falha de persistência — cada um com o exit code correto e sem persistir/imprimir quando aplicável | T006 | | #2 |
| ~~T008~~ | Documentar o procedimento operacional de backup do pepper do HMAC (README ou runbook — fora do código) | — | [P] | #2 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em paralelo.
- Todas as tarefas desta feature estão concluídas — ver `plan.md` para as decisões técnicas
  tomadas ao longo da implementação (parsing manual de argumentos, separação entre
  `GenerateCommand` (lógica pura, retorna exit code) e `ApiKeyCliRunner` (adaptador que chama
  `System.exit`), variável `API_KEY_HMAC_PEPPER`).
