# Data model: revoke-api-key

## Entidades

### ApiKey (tabela `api_keys`) — alteração

Acrescenta uma coluna à tabela criada em `001-generate-api-key`:

| Campo | Tipo | Obrigatório | Validação |
|---|---|---|---|
| `revoked_at` | TIMESTAMP (UTC) | não | nulo = nunca revogada nem agendada; no passado = já revogada; no futuro = revogação agendada |

Migration Flyway **nova** (`V2`, nos dois diretórios paralelos — `db/migration` para
PostgreSQL, `db/migration-h2` para o perfil `sandbox`), não uma edição de `V1`: ao contrário
da renomeação `service_name` → `client_name` (feita editando `V1` direto, coberta pela seção
"Status do sistema: pré-produção" de `memory/constitution.md`), esta é uma coluna nova para
uma capacidade nova, não a correção de um nome errado em algo que `001` já define — manter
`V1` como o registro exato do que `001` entregou, e `V2` como o que `002` acrescenta, preserva
a trilha de auditoria de qual feature trouxe o quê.

```sql
ALTER TABLE api_keys ADD COLUMN revoked_at TIMESTAMP;
```

## Relacionamentos

Inalterado em relação a `001` — tabela isolada.

## Invariantes

- `revoked_at`, quando não nulo, pode estar no passado (já revogada) ou no futuro (agendada).
- `revoked_at`, quando não nulo, e `expires_at`, quando não nulo, obedecem `revoked_at <=
  expires_at` — uma chave nunca tem uma revogação (efetiva ou agendada) marcada para depois do
  próprio prazo de validade. Garantido pela aplicação antes de persistir (`RevokeCommand`
  recusa tanto revogar uma chave já expirada quanto calcular um `revoked_at` que ultrapasse
  `expires_at`), não por uma constraint de banco.
- Uma chave nunca é removida da tabela ao ser revogada — `revoked_at` é a única mudança;
  `key_hash`, `client_name` e `created_at` permanecem como estavam.
- Reagendar (`revoke` chamado de novo numa chave com `revoked_at` no futuro) sempre sobrescreve
  o valor anterior — não há histórico de agendamentos passados, só o valor vigente.
