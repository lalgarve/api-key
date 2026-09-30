# Plan: list-api-keys

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Comando somente leitura — nenhuma mudança de schema além da coluna `revoked_at` já adicionada
por `002-revoke-api-key` (T001). Depende também da tabela de despacho de comandos da CLI
(`002`, T002), onde `ListCommand` se registra como um terceiro comando ao lado de `generate` e
`revoke`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Formato de saída | Texto tabular alinhado por colunas, sem JSON | resolvida | Consistente com a saída de `generate`/`revoke`, já em texto simples; um `--format json` fica para quando (se) surgir um consumidor automatizado — não pedido agora. |
| Como o status (ativa/revogada) é derivado | Calculado em memória a partir de `revoked_at` comparado a `Instant.now()` no momento da consulta — não é uma coluna própria | resolvida | Evita um job/trigger para manter um campo de status sincronizado; mesmo padrão implícito já usado para `expires_at` desde `001` (a checagem é responsabilidade de quem lê, não de uma coluna derivada persistida). |
| Ordenação padrão | `created_at` ascendente | resolvida | Ordem cronológica de emissão — previsível, sem precisar de uma flag extra agora. |
| Como filtrar por status/cliente/janela de revogação no banco | Consulta única (`findAll`/`findByClientName`, conforme presença de `--client`) seguida de filtragem em memória para status e janela de revogação | resolvida | Mesmo racional de escala pequena já documentado em `001`/`003` (poucos clientes, poucas chaves por cliente) — não compensa uma consulta JPQL/Criteria dinâmica para combinar poucos filtros opcionais. |

## Estrutura de módulos/pacotes

`ListCommand` no pacote `io.deployo.apikey.management` (mesma frente de "Gestão" introduzida
em `002-revoke-api-key` para `RevokeCommand`), registrado na tabela de despacho de
`ApiKeyCliRunner`.

## Riscos e trade-offs

- Filtrar em memória em vez de no banco não escala para um volume grande de chaves — aceitável
  dado o contexto declarado no README (poucos clientes esperados); revisitar se isso mudar.
- Misturar o conceito de "ativa" (não revogada) com "válida" (não revogada e não expirada) foi
  conscientemente evitado nesta versão (ver decisão em aberto em `spec.md`) — a listagem
  reflete só revogação, podendo mostrar como "active" uma chave que uma leitura real já
  rejeitaria por expiração.
