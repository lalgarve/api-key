# deployo-api-key

Biblioteca e aplicação Java para gerar API-KEY pela linha de comando.

## Contexto e propósito

Este projeto nasce dentro do ecossistema do [`jogo-acoes`](https://github.com/lalgarve/jogo-acoes),
que hoje autentica usuários por link mágico enviado por e-mail. O envio de e-mail está sendo
extraído para um serviço próprio — e esse serviço precisa de uma forma de restringir quem
pode chamá-lo. A solução escolhida é autenticação via API-KEY.

Em vez de resolver isso só para o serviço de e-mail, o objetivo é ter um padrão que qualquer
serviço interno possa adotar para restringir quem pode chamá-lo: cada serviço protegido roda
sua própria cópia deste projeto — mesmo container, banco de dados próprio, nunca compartilhado
entre serviços diferentes — e usa o comando `generate` para emitir uma chave por **cliente**
autorizado (ex.: `jogo-acoes` chamando o serviço de e-mail; se amanhã existir um serviço de
cobrança, ele teria sua própria instância e seus próprios clientes). Como o número de clientes
que vão precisar de chave é pequeno por enquanto (1, o próprio `jogo-acoes`), uma interface de
administração foi conscientemente deixada de fora do escopo inicial: a linha de comando já
resolve o problema real sem o custo de construir e manter uma UI que ninguém usaria ainda.

Por ser um projeto pequeno e autocontido, ele também serve como **exemplo compacto de
Spec-Driven Development (SDD)** — a metodologia usada aqui, mais fácil de avaliar de ponta a
ponta do que o `jogo-acoes` (que já é grande e usa uma abordagem correlata, BDD + DER + OpenAPI
escritos antes da implementação, mas não formalizada como SDD). A intenção é usar este
repositório para pedir ao professor da disciplina autorização para adotar SDD como
metodologia — ver o [documento de alinhamento do jogo-acoes](https://github.com/lalgarve/jogo-acoes/blob/docs/alinhamento-projeto-disciplina/docs/context/alinhamento-projeto-disciplina.md)
para o contexto acadêmico completo.

## Uso

```
export API_KEY_HMAC_PEPPER=<segredo-do-ambiente>
java -jar deployo-api-key.jar generate --client jogo-acoes [--validity-days 90]
```

A chave em texto puro é impressa **uma única vez**, na hora da geração — guarde-a
imediatamente, não há como recuperá-la depois. Ver
[`specs/001-generate-api-key/contracts/cli-commands.md`](specs/001-generate-api-key/contracts/cli-commands.md)
para o contrato completo (argumentos, saída, exit codes).

## Operação: backup do pepper do HMAC

O hash de cada chave é calculado com HMAC-SHA256 usando um pepper lido da variável de
ambiente `API_KEY_HMAC_PEPPER` — mantido fora do banco de dados e do código-fonte de
propósito (ver `specs/001-generate-api-key/plan.md`, "Onde fica o pepper do HMAC").

**Perder o pepper é irreversível.** Sem ele, nenhum hash já persistido pode ser recalculado
para validação — é equivalente a perder todas as chaves já emitidas; cada cliente precisaria
receber uma chave nova. Trate o valor do pepper como um segredo crítico:

- Guarde-o no gerenciador de segredos do ambiente onde este serviço roda (nunca em
  repositório de código, nem em log).
- Faça backup do pepper junto com — mas separado do — backup do banco de dados: os dois
  juntos permitem restaurar a capacidade de validar chaves; o banco sozinho não.
- Ao rotacionar o pepper deliberadamente, todas as chaves já emitidas deixam de validar —
  equivalente a revogar todas de uma vez. Não há suporte (ainda) a múltiplos peppers válidos
  simultaneamente para uma rotação gradual.

## Metodologia de desenvolvimento

Este projeto usa Spec-Driven Development (SDD):

- [`memory/constitution.md`](memory/constitution.md) — convenções do projeto (idioma,
  commits, branches/PR, testes, rastreamento de trabalho via Issues).
- [`specs/`](specs/) — spec, plano técnico e tarefas de cada feature, escritos antes da
  implementação.
- [`templates/`](templates/) — modelos usados para começar uma feature nova.

## Uso de ferramentas de IA

Conforme a política "Sinal Verde" da disciplina (mesma adotada no `jogo-acoes`), o
desenvolvimento deste projeto conta com apoio de ferramentas de IA (Claude Code, Anthropic)
— incluindo a própria definição da estrutura de SDD usada aqui. Todo conteúdo gerado ou
revisado com apoio de IA é lido, entendido e validado antes de ser incorporado ao
repositório; commits e decisões de arquitetura permanecem de responsabilidade da autora.
