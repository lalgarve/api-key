# Plan: generate-api-key

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Aplicação de linha de comando em Java, de execução curta (roda, grava uma linha, sai) — não
um serviço de longa duração. Cada serviço que precisa restringir quem pode chamá-lo roda sua
própria cópia deste projeto, no mesmo container/ambiente onde ele próprio roda, com seu
próprio banco de dados — nunca uma instância ou um banco compartilhado entre serviços
diferentes. O próprio serviço protegido é quem faz leitura (`SELECT`) nesse banco local, via a
futura biblioteca de leitura, para validar as chamadas que recebe de clientes (ex.:
`jogo-acoes` chamando o serviço de e-mail).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Algoritmo de hash da chave | HMAC-SHA256 com pepper | resolvida | A chave já nasce com alta entropia (256 bits aleatórios) — diferente de senha de usuário, não precisa de um hash memory-hard/lento como Argon2, cujo custo defende principalmente contra senhas fracas e ataques de dicionário. HMAC-SHA256 é suficiente, mais simples e sem dependência externa (`javax.crypto` já no JDK). |
| Onde fica o pepper do HMAC | Variável de ambiente `API_KEY_HMAC_PEPPER`, nunca no banco nem no código-fonte | resolvida | Se a tabela de chaves vazar sozinha, os hashes continuam inúteis sem o pepper — motivo de existir separado do salt/hash armazenado. |
| Prefixo da chave gerada | `dak_` ("Deployo API Key") | resolvida | Prefixo genérico do projeto, não amarrado ao primeiro consumidor (serviço de e-mail) — o objetivo declarado é um padrão reutilizável entre APIs internas futuras. |
| Tamanho da chave | 32 bytes de entropia aleatória (256 bits), codificados em base64url, com o prefixo `dak_` concatenado antes | resolvida | Padrão de mercado para tokens de API (GitHub, Stripe usam entropia equivalente ou maior). |
| Mecanismo de acesso a banco | Spring Data JPA + Hibernate | resolvida | Mesmo padrão do `jogo-acoes` — reaproveita conhecimento e convenções já estabelecidos entre os dois projetos do portfólio. O custo de start-up de um contexto Spring é irrelevante aqui: a CLI roda raramente, sob operação manual, não em um hot path. |
| Motor de banco de dados | PostgreSQL nos perfis com infraestrutura real (`docker`, usado também em CI); H2 embarcado no perfil `sandbox` | resolvida | Mesmo padrão de nomenclatura de ambientes já adotado neste projeto (ver "Nomenclatura de ambientes" em `memory/constitution.md`) e replicado do `jogo-acoes` — H2 permite rodar testes/CLI sem depender de infraestrutura externa, Postgres real valida contra o motor efetivamente usado em produção. |
| Ferramenta de migration | Flyway | resolvida | Mesma ferramenta do `jogo-acoes` — migrations versionadas em `src/main/resources/db/migration`, aplicadas automaticamente pelo Spring Boot na subida da aplicação. |
| Biblioteca de parsing de argumentos da CLI | Nenhuma — parsing manual de `String[] args` | resolvida | Só dois argumentos (`--client`, `--validity-days`); o overhead de uma lib externa (picocli, etc.) não se paga pra essa superfície. Reavaliar se a CLI ganhar mais comandos/flags no futuro. |
| Como obter exit codes precisos (0/1/2/3) sem quebrar a testabilidade de `main()` | `GenerateCommand.execute(args, out, err)` retorna o exit code (lógica pura, 100% testável sem tocar o processo); `ApiKeyCliRunner` (thin adapter, `CommandLineRunner`) chama `ProcessExiter.exit(code)` só quando o código não é zero | resolvida | `SpringApplication.exit()`/`System.exit()` chamado direto dentro do fluxo normal mataria a JVM de teste sempre que `DeployoApiKeyApplicationTests` invocasse `main()` num caminho de erro — por isso o smoke test (T001) só exercita o caminho de sucesso (código 0, nunca chama `exit`), e todo caminho de falha é testado chamando `GenerateCommand` diretamente, sem passar por `main()`. `ProcessExiter` é a interface que torna até o `ApiKeyCliRunner` testável sem sair do processo de teste. |

Tarefas que dependem de uma decisão "em aberto" ficam bloqueadas nela em `tasks.md` — a
decisão vira commit `decision:` quando resolvida, atualizando esta tabela no mesmo commit.
Nenhuma decisão desta feature ficou em aberto.

## Estrutura de módulos/pacotes

O repositório tem (pelo menos) duas frentes de código, que devem ficar em pacotes/módulos
Maven separados desde já, mesmo com a leitura ainda fora de escopo desta feature:

- **Emissão** (esta feature): gera a chave, calcula o hash, persiste, expõe a CLI.
- **Leitura** (feature futura, biblioteca): só lê pelo hash para validar uma chave recebida
  — usada como dependência pelo serviço protegido (ex.: o serviço de e-mail), rodando no
  mesmo container/banco dessa instância, sem trazer a lógica de geração/CLI junto.

## Riscos e trade-offs

- **Perda do pepper é irreversível**: sem ele, nenhum hash já persistido pode ser validado
  novamente (não há como re-derivar as chaves originais). Mitigação é operacional (backup do
  valor do pepper), não uma feature de código — registrar isso como procedimento no README ou
  runbook quando a implementação acontecer.
- Rodar Spring Boot completo para uma CLI de execução curta adiciona alguns segundos de
  start-up por chamada — aceito conscientemente em troca de consistência com o `jogo-acoes`.
