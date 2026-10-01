# Plan: auto-revoke-on-rotation

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Depende diretamente de `002-revoke-api-key`: usa a coluna `revoked_at` definida lá (T001) e a
tabela de despacho de comandos da CLI (T002) — nenhuma migration nova nem refatoração de
despacho nesta feature. Modifica o comando `generate` existente (`io.deployo.apikey.issuance`,
de `001-generate-api-key`), acrescentando um passo antes da persistência da chave nova.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde roda a lógica de auto-revogação | Dentro do fluxo de `generate`, **antes** de persistir a chave nova, na mesma transação | resolvida | Rodar depois (tentativa inicial) faz a própria chave recém-criada já compartilhar o `client_name` buscado — a busca por "chaves ativas do cliente" se auto-inclui e revoga a chave que acabou de nascer. Rodar antes evita isso: a consulta só vê chaves que já existiam. Atende o FR6/FR7 (atomicidade) sem mecanismo de coordenação separado — mesma transação cobre os dois passos, em qualquer ordem. |
| Como buscar as chaves ativas de um cliente | `ApiKeyRepository.findByClientName(String clientName)` (consulta derivada simples) seguida de filtro em memória com `ApiKey.isRevoked(Instant)` | resolvida | A decisão original cogitava um único método derivado combinando `clientName` com `revokedAt IS NULL OR revokedAt > :now` — mas o Spring Data interpreta `And`/`Or` em nomes de método da esquerda para a direita, sem agrupamento: `ClientNameAndRevokedAtIsNullOrRevokedAtGreaterThan` significa `(clientName = ? AND revokedAt IS NULL) OR revokedAt > ?`, não `clientName = ? AND (revokedAt IS NULL OR revokedAt > ?)` — alcançaria chaves de outros clientes também. Buscar por cliente e filtrar "ativa" em memória evita essa armadilha; mesmo racional de volume pequeno já usado em `004-list-api-keys`. |
| Onde vive a lógica de "calcular novo `revoked_at` sem adiar um agendamento existente" (FR4) | Colaborador dedicado, não dentro de `GenerateCommand` diretamente | resolvida | `GenerateCommand` já orquestra vários passos (geração, hash, validade, persistência); isolar essa regra num colaborador próprio mantém cada peça testável isoladamente em vez de crescer ainda mais uma classe já com várias responsabilidades. |
| Transação cobrindo geração + revogação das antigas | `@Transactional` no método de execução do comando | resolvida | Requisito direto do FR7; sem isso, uma falha a meio do processo deixaria estado parcial (chave nova viva, revogação de uma antiga perdida). |
| Valor padrão de `N` dias de carência | Não existe — `--revoke-old-in-days` é opcional e, quando omitido, a rotina inteira não roda (FR1) | resolvida | Evita a pergunta de produto "qual carência padrão" por completo, e dobra como o próprio mecanismo de opt-out: gerar sem o argumento nunca tem efeito colateral, sem precisar de uma flag separada. |

## Estrutura de módulos/pacotes

Novo colaborador na frente de **Emissão** (`io.deployo.apikey.issuance`, mesma frente de
`GenerateCommand`) — por exemplo `OldKeyRotationPolicy` — injetado em `GenerateCommand` e
responsável por: buscar as chaves ativas do cliente (via `ApiKeyRepository`, decisão acima),
calcular o novo `revoked_at` respeitando FR4, e persistir a atualização. Fica em `issuance`, e
não em `management` (`002-revoke-api-key`), porque é acionado pelo fluxo de `generate`, não
pelo fluxo de `revoke` — mesmo dado (`revoked_at`) e mesma tabela, front diferente.

## Riscos e trade-offs

- Como a rotina é opt-in por chamada (FR1), um operador que queira rotacionar precisa lembrar
  de passar `--revoke-old-in-days` toda vez — o trade-off inverso da versão anterior deste
  plano (que arriscava surpreender clientes com múltiplas chaves de propósito). Aceito
  conscientemente: nunca alterar uma chave existente sem pedido explícito é mais seguro por
  padrão do que automatizar silenciosamente, mesmo custando a conveniência de um padrão
  automático para o caso comum de rotação.
- Buscar e atualizar múltiplas linhas dentro da mesma transação que insere a chave nova
  aumenta o escopo de bloqueio da transação de `generate` — aceitável dado o volume pequeno
  esperado (poucas chaves por cliente), revisitar se isso mudar.
