# Plan: revoke-api-key

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Mesma aplicação de linha de comando de execução curta de `001-generate-api-key`, ganhando um
segundo comando (`revoke`) além de `generate`. Até aqui, `ApiKeyCliRunner` despachava um único
comando de forma implícita (`GenerateCommand` checava `args[0] == "generate"` e retornava 0
silenciosamente para qualquer outra coisa) — isso deixa de fazer sentido com um segundo
comando real.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como identificar a chave a revogar | `--id <id>`, a chave primária numérica da tabela `api_keys` | resolvida | Já existe como identificador estável e não sensível — ao contrário do hash, é seguro de expor. A descoberta do id (quais chaves existem, de qual cliente) fica a cargo do comando `list` (`004-list-api-keys`); esta feature só consome o id, não resolve como encontrá-lo. |
| Como despachar múltiplos comandos na mesma CLI | Interface `CliCommand` (mesma assinatura de `GenerateCommand.execute(args, out, err)`), com uma tabela de despacho `Map<String, CliCommand>` por `args[0]` dentro de `ApiKeyCliRunner` | resolvida | Generaliza o padrão de retorno de exit code já validado em `001` (ver decisão de testabilidade lá) agora que existe um segundo comando de verdade — evita um `if/else` crescendo a cada comando novo. `GenerateCommand` e o novo `RevokeCommand` passam a implementar `CliCommand`. |
| O que fazer quando `--in-days` é informado e a chave já tem uma revogação futura agendada | Sobrescreve (reagenda) `revoked_at` para o novo valor calculado — sem comparar com o valor anterior | resolvida | Permite ao operador corrigir/ajustar uma revogação já agendada com o mesmo comando, sem precisar de uma flag separada de "reagendar". |
| Revogar uma chave já revogada (no passado) | Falha com erro — nada é alterado | resolvida | Evita mascarar um engano do operador (ex.: achar que está revogando algo que na verdade já foi revogado por outro motivo/sessão). |
| Onde vive a lógica de revogação em relação a `issuance` | `RevokeCommand` num novo pacote `io.deployo.apikey.management`, reutilizando `ApiKey`/`ApiKeyRepository` de `io.deployo.apikey.issuance` sem movê-los | resolvida | `ApiKey`/`ApiKeyRepository` já existem em `issuance`; só há hoje uma segunda frente consumindo-os (`management`), não justifica extrair para um pacote neutro ainda — revisitar quando a frente de "Leitura" (validação, `001`/plan.md) também precisar deles. |

Decisões marcadas "em aberto" em `spec.md` (comportamento com chave expirada; múltiplas
chaves ativas por cliente) são de produto, não técnicas — bloqueiam T000 desta feature, não
esta tabela.

## Estrutura de módulos/pacotes

- **Emissão** (`io.deployo.apikey.issuance`, `001`): inalterada.
- **Gestão** (`io.deployo.apikey.management`, nova): `RevokeCommand` — e, quando implementado,
  `ListCommand` (`004`) — operam sobre chaves já emitidas (revogar, listar), sem gerar chaves
  novas.
- **Leitura** (futura, fora de escopo): segue não implementada.

`ApiKeyCliRunner` passa a viver num nível acima das duas frentes (despacha para
`issuance.GenerateCommand` ou `management.RevokeCommand` conforme `args[0]`).

## Riscos e trade-offs

- Não existe "desfazer" uma revogação — um erro do operador só se corrige gerando uma chave
  nova para o cliente, não revertendo a revogação. Aceito conscientemente: um comando
  `unrevoke` reabriria a pergunta de por quanto tempo uma chave pode ficar "revogada mas
  reversível", que não tem resposta óbvia e não foi pedida.
- A refatoração do despacho de comandos (`CliCommand`/tabela de despacho) toca
  `ApiKeyCliRunner`, já coberto por testes de `001` — validar que os testes existentes
  (`ApiKeyCliRunnerTest`) continuam passando após a mudança de forma, não só de comportamento.
