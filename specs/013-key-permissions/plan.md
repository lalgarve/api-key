# Plan: key-permissions

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Entra na 1.1.0, junto com `010-validate-api-key-cli` (já mesclada na `main`). Toca os três
módulos:

- `api-key-core`: tabela nova, `ApiKey` ganha as permissões, `PermissionCatalog` novo (lido
  pela CLI e pela biblioteca).
- `api-key-validation`: `Valid` ganha as permissões; `ApiKeyValidator` passa a ler o catálogo.
- `api-key-cli`: `generate` ganha `--permissions`; `list` e `validate` mostram as permissões;
  o runner recusa um catálogo inválido.

Restrições que já existem e moldam as decisões abaixo:

- `ddl-auto: validate`: o mapeamento JPA tem que bater com a migration nova.
- O serviço protegido acha os beans por `scanBasePackages` em `dev.leilaalgarve.apikey.core` e
  `.validation` (`008-validate-api-key/http-integration.md`). Ainda não há auto-configuração.
- Erros de configuração já seguem um padrão: o `ApiKeyHasher` não impede o contexto de subir
  sem pepper; ele lança `MissingHmacPepperException` quando é usado, e a CLI traduz para exit 2.
- `CliArgs.extractOption` lê uma ocorrência por opção.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Chaves emitidas antes da 1.1.0 | Recebem as permissões padrão do catálogo, calculadas na leitura (FR13) | resolvida (spec, Leila) | Ver "Decisões em aberto" de `spec.md`. |
| Formato da opção | `--permissions a,b` | resolvida (spec, Leila) | Encaixa em `CliArgs.extractOption`. |
| Como gravar as permissões | Tabela `api_key_permissions (api_key_id, permission)`, mapeada como `@ElementCollection` de `Set<String>` em `ApiKey` (`@CollectionTable`), sem entidade própria | resolvida | As permissões não têm identidade nem ciclo de vida fora da chave, e nunca mudam depois de gravadas (FR10). Uma entidade `ApiKeyPermission` com repositório próprio só acrescentaria código. Uma coluna única com a lista separada por vírgula (`VARCHAR`/`TEXT[]`) evitaria a tabela, mas perderia a chave primária que impede repetição e tornaria um filtro futuro por permissão (fora de escopo agora) uma busca em texto. |
| Como evitar N+1 em `list` | `@ElementCollection(fetch = EAGER)` com `@Fetch(FetchMode.SUBSELECT)` | resolvida | `list` carrega todas as chaves com `findAll`/`findByClientName`. Com `SUBSELECT`, o Hibernate busca as permissões de todas elas numa consulta só, não uma por chave. `EAGER` evita `LazyInitializationException`: `list` e `ApiKeyValidator` rodam fora de transação (`open-in-view: false`). A alternativa, `LAZY` com `@EntityGraph` em cada método do repositório, quebraria em silêncio no primeiro método novo que esquecesse o grafo. O custo é uma consulta a mais em `revoke` (que não usa as permissões), desprezível. |
| Onde o catálogo é lido | `PermissionCatalog` em `api-key-core` (`@Component`), que lê `api-key.permissions` pelo `Binder` do Spring no construtor | resolvida | `api-key-core` já é escaneado pela CLI e pelo serviço, então o bean aparece nos dois sem mudar a configuração do serviço. Ler pelo `Binder` mantém a classe imutável e não exige `@EnableConfigurationProperties` (que o serviço teria que declarar). Formato: lista de `{name, default}`; YAML e variáveis de ambiente (`API_KEY_PERMISSIONS_0_NAME`) funcionam do mesmo jeito. |
| O que fazer com um catálogo inválido | O contexto sobe mesmo assim. `PermissionCatalog` guarda o problema e lança `InvalidPermissionCatalogException` quando é usado. `ApiKeyCliRunner` chama `catalog.requireValid()` depois de achar o comando e antes de despachar, e traduz para exit 2 com a mensagem do contrato | resolvida | Falhar na criação do bean faria o Spring abortar com stack trace e exit 1, quebrando o contrato (exit 2 com mensagem fixa). Checar no runner cobre todos os comandos num lugar só (FR3), inclusive `revoke`, que não usa permissão. No serviço, `validate` lança a exceção, como já faz com o pepper. |
| Onde mora a regra "gravadas, ou padrão se não houver nenhuma" | `PermissionCatalog.effectivePermissions(ApiKey)` | resolvida | `ApiKeyValidator` e `ListCommand` precisam da mesma resposta (FR15: `list` mostra o que o validador devolveria). Uma regra só, em `api-key-core`, que os dois módulos já usam. |
| Como `Valid` ganha as permissões sem quebrar quem usa | Componente novo `Set<String> permissions` no record, cópia imutável no construtor compacto; construtor de um argumento mantido (conjunto vazio); `hasPermission(String)` | resolvida | Ver `contracts/validation-api.md`. Única quebra: desconstrução por padrão de record, registrada na nota de release. Uma classe nova (`ValidWithPermissions`) ou um subtipo novo em `ApiKeyValidationResult` quebraria todo `switch` exaustivo dos consumidores, que é o uso documentado. |
| Construtores de `ApiKeyValidator` | Mantém o de três argumentos (sem catálogo, comportamento 1.0.x). Novo de quatro (com `PermissionCatalog`). O `@Autowired` passa a receber o `PermissionCatalog` | resolvida | Testes que criam o validador à mão continuam compilando. O construtor do Spring não é chamado por quem consome. |
| Onde o `generate` valida `--permissions` | Em `GenerateCommand`, logo depois de `--revoke-old-in-days` e antes de gerar a chave. Parsing (dividir por vírgula, `strip`, descartar vazios, deduplicar mantendo a ordem para achar "o primeiro desconhecido") num método estático de `CliArgs` (`parseCommaList`) | resolvida | Mesma sequência dos outros erros de uso: tudo validado antes de gerar e gravar, e nada impresso em caso de erro (FR7). `CliArgs` já reúne os parsers compartilhados. |
| Ordem das permissões na saída | Alfabética, `TreeSet`, em `generate`, `list` e `validate` | resolvida | Saída determinística, sem depender da ordem do banco nem da ordem digitada. |
| Como os cenários Cucumber trocam de catálogo | O step `Given the permission catalog is:` (ou `no permission catalog is configured`) guarda um `PermissionCatalog` em `ScenarioState`. `CommandDispatchSteps` monta `GenerateCommand`, `ListCommand` e `ValidateCommand` (com um `ApiKeyValidator` próprio) com esse catálogo e os demais beans reais | resolvida | O contexto Spring do Cucumber é um só para a suíte inteira, e o catálogo é imutável. Montar o comando por cenário é o mesmo recurso já usado para o pepper ausente (`CucumberSpringConfiguration`). Os cenários "is validated by the protected service" chamam esse mesmo `ApiKeyValidator`. |
| Prova de compatibilidade | Os `.feature` existentes (Cucumber e caixa-preta) rodam sem catálogo e não mudam. A suíte caixa-preta ganha um `permissions.feature` que passa o catálogo por variáveis de ambiente | resolvida | Se algum cenário existente precisar mudar, é sinal de quebra de compatibilidade (NFR de `spec.md`). |

## Estrutura de módulos/pacotes

**`api-key-core`** (`dev.leilaalgarve.apikey.core`)

- `db/migration-api-key/V3__create_api_key_permissions_table.sql`:
  ```sql
  CREATE TABLE api_key_permissions (
      api_key_id BIGINT NOT NULL REFERENCES api_keys (id) ON DELETE CASCADE,
      permission VARCHAR(100) NOT NULL,
      PRIMARY KEY (api_key_id, permission)
  );
  ```
- `ApiKey`: campo `permissions` (`@ElementCollection`, `@CollectionTable(name =
  "api_key_permissions", joinColumns = @JoinColumn(name = "api_key_id"))`, `@Column(name =
  "permission")`, `EAGER` + `SUBSELECT`). Construtor novo com `Set<String> permissions`; o
  atual delega com `Set.of()`. `getPermissions()` devolve uma cópia imutável ordenada.
- Novo `PermissionCatalog` e `InvalidPermissionCatalogException` (contrato em
  `contracts/validation-api.md`). Mensagens da exceção = mensagens do contrato de CLI.

**`api-key-validation`** (`dev.leilaalgarve.apikey.validation`)

- `ApiKeyValidationResult.Valid`: componente `permissions`, construtor de um argumento,
  `hasPermission`.
- `ApiKeyValidator`: construtores conforme a tabela; `validate` devolve
  `new Valid(key.getClientName(), catalog.effectivePermissions(key))`.

**`api-key-cli`** (`dev.leilaalgarve.apikey`)

- `CliArgs.parseCommaList(String)`.
- `ApiKeyCliRunner`: recebe `PermissionCatalog`; `requireValid()` antes de despachar; exit 2.
- `issuance.GenerateCommand`: recebe `PermissionCatalog`; valida `--permissions`; grava a chave
  com as permissões; última linha `Permissions: ...`.
- `management.ListCommand`: recebe `PermissionCatalog`; coluna `PERMISSIONS` quando
  `isConfigured()`.
- `management.ValidateCommand`: segunda linha `Permissions: ...` quando `Valid.permissions()`
  não é vazio.

**Testes**

- `api-key-cli/src/test/resources/features/key-permissions.feature` (cenários de `spec.md`,
  que passa a apontar para o arquivo em vez de repetir o Gherkin, como nas outras specs).
- `blackbox-tests/features/permissions.feature`, com o catálogo em
  `API_KEY_PERMISSIONS_<n>_NAME`/`_DEFAULT`.
- Unitários: `PermissionCatalogTest` (cada regra de FR2/FR3/FR4 e `effectivePermissions`),
  `ApiKeyValidatorTest` (as três linhas da tabela do contrato), testes de `GenerateCommand`,
  `ListCommand`, `ValidateCommand` e `ApiKeyCliRunner` para os ramos novos.

## Riscos e trade-offs

- **Ordem do upgrade.** Com `ddl-auto: validate`, um serviço que suba a biblioteca 1.1.0 num
  banco ainda sem a V3 não inicia (a tabela mapeada não existe). As migrations só rodam pela
  CLI. Ordem certa: rodar qualquer comando da CLI 1.1.0 (ex.: `list`) antes de publicar o
  serviço com a biblioteca 1.1.0. Vai para o README e para a nota de release.
- **Padrão que muda depois.** Chaves sem permissão gravada acompanham as permissões padrão do
  catálogo. Mudar o padrão muda o que essas chaves podem fazer (aceito na decisão da spec).
  Para congelar, o operador gera chave nova com `--permissions` explícito.
- **CLI e serviço precisam do mesmo catálogo.** Se divergirem, `list` mostra uma coisa e o
  serviço aplica outra para as chaves antigas. Igual ao pepper: tem que ser a mesma
  configuração nos dois. Vai para `http-integration.md`.
- **Padrão de record.** `case Valid(String clientName)` deixa de compilar. Aceito; nota de
  release.
- **`EAGER` em todo lugar.** Toda leitura de `ApiKey` traz as permissões. Com o volume esperado
  (poucos clientes por instância), não pesa.
