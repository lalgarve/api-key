# Plan: auto-revoke-on-rotation

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Depende diretamente de `002-revoke-api-key`: usa a coluna `revoked_at` definida lá (T001) e a
tabela de despacho de comandos da CLI (T002) — nenhuma migration nova nem refatoração de
despacho nesta feature. Modifica o comando `generate` existente (`io.deployo.apikey.issuance`,
de `001-generate-api-key`), acrescentando um passo depois da persistência da chave nova.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde roda a lógica de auto-revogação | Dentro do fluxo de `generate`, depois de persistir a chave nova, na mesma transação | resolvida | Atende o FR6 (atomicidade) sem introduzir um mecanismo de coordenação separado — já é o padrão natural de uma transação Spring Data JPA. |
| Como buscar as chaves ativas de um cliente | Método derivado em `ApiKeyRepository`: `findByClientNameAndRevokedAtIsNullOrRevokedAtGreaterThan(String clientName, Instant now)` | resolvida | Volume de chaves por cliente é pequeno (poucas dezenas no limite, dado o contexto do projeto — ver README); não compensa uma consulta JPQL/Criteria customizada para este filtro. |
| Onde vive a lógica de "calcular novo `revoked_at` sem adiar um agendamento existente" (FR4) | Colaborador dedicado, não dentro de `GenerateCommand` diretamente | resolvida | `GenerateCommand` já orquestra vários passos (geração, hash, validade, persistência); isolar essa regra num colaborador próprio mantém cada peça testável isoladamente em vez de crescer ainda mais uma classe já com várias responsabilidades. |
| Transação cobrindo geração + revogação das antigas | `@Transactional` no método de execução do comando | resolvida | Requisito direto do FR6; sem isso, uma falha a meio do processo deixaria estado parcial (chave nova viva, revogação de uma antiga perdida). |
| Valor padrão de `N` dias de carência | em aberto — ver `spec.md` | em aberto | Decisão de produto, não técnica; bloqueia a implementação de FR2. |

## Estrutura de módulos/pacotes

Novo colaborador na frente de **Emissão** (`io.deployo.apikey.issuance`, mesma frente de
`GenerateCommand`) — por exemplo `OldKeyRotationPolicy` — injetado em `GenerateCommand` e
responsável por: buscar as chaves ativas do cliente (via `ApiKeyRepository`, decisão acima),
calcular o novo `revoked_at` respeitando FR4, e persistir a atualização. Fica em `issuance`, e
não em `management` (`002-revoke-api-key`), porque é acionado pelo fluxo de `generate`, não
pelo fluxo de `revoke` — mesmo dado (`revoked_at`) e mesma tabela, front diferente.

## Riscos e trade-offs

- Sem uma flag de opt-out (ver decisão em aberto em `spec.md`), um cliente que legitimamente
  precise de múltiplas chaves ativas simultâneas e de propósito (não uma rotação em
  andamento) seria surpreendido por chaves antigas sendo agendadas para revogação a cada
  `generate`. Mitigação até a decisão ser tomada: nenhuma — aceitar o risco de surpresa é
  consciente até o valor padrão e o mecanismo de opt-out (se houver) serem definidos.
- Buscar e atualizar múltiplas linhas dentro da mesma transação que insere a chave nova
  aumenta o escopo de bloqueio da transação de `generate` — aceitável dado o volume pequeno
  esperado (poucas chaves por cliente), revisitar se isso mudar.
