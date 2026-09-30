# Spec: generate-api-key

**Status:** aprovada — implementada (T000-T008 concluídas)
**Issue:** #2

## Resumo

Uma aplicação de linha de comando gera uma nova API-KEY para um cliente identificado, grava o
hash dessa chave no banco de dados, e mostra a chave em texto puro ao operador exatamente uma
vez — esse é o único momento em que ela existe fora do banco em forma recuperável.

## Motivação

O serviço de e-mail sendo extraído do `jogo-acoes` precisa restringir quem pode chamá-lo.
Cada serviço que adota esse padrão roda sua própria cópia deste projeto — mesmo container,
banco de dados próprio — nunca uma instância nem um banco compartilhado entre serviços
diferentes; é isso que torna a instância inteira sobre "quem pode chamar este serviço
específico". Dentro dela, o comando `generate` emite uma chave para um **cliente** — quem vai
chamar o serviço onde esta instância está rodando (ex.: `jogo-acoes` chamando o serviço de
e-mail; se amanhã existir um serviço de cobrança, ele teria sua própria instância e seus
próprios clientes). O próprio serviço protegido é quem faz leitura (`SELECT`) no seu banco
local para validar as chamadas que recebe, através de uma biblioteca de leitura própria (fora
do escopo desta feature — ver "Fora de escopo" abaixo).

## Cenários (comportamento esperado)

```gherkin
Scenario: generate a new key for a named client
  Given no operator input beyond a valid client name
  When the operator runs "generate --client jogo-acoes"
  Then a new row is persisted with the key's hash, the client name "jogo-acoes", and the creation timestamp
  And the plaintext key is printed to stdout exactly once
  And the plaintext key is never written to the database or to any log

Scenario: client name is required
  Given the operator runs "generate" without "--client"
  When the command is executed
  Then it fails with a usage-error exit code
  And no row is persisted

Scenario: client name is blank
  Given the operator runs "generate --client " with an empty/whitespace-only value
  When the command is executed
  Then it fails with a usage-error exit code
  And no row is persisted

Scenario: HMAC pepper is not configured
  Given the environment variable holding the HMAC pepper is not set
  When the operator runs "generate --client jogo-acoes"
  Then the command fails with a configuration-error exit code
  And no row is persisted
  And no plaintext key is printed

Scenario: generate a key with a validity period
  Given the operator runs "generate --client jogo-acoes --validity-days 90"
  When the command is executed
  Then a new row is persisted with expires_at set to 90 days after the creation timestamp

Scenario: generate a key without a validity period
  Given the operator runs "generate --client jogo-acoes" without "--validity-days"
  When the command is executed
  Then a new row is persisted with expires_at set to null (no expiration)

Scenario: validity in days must be a positive integer
  Given the operator runs "generate --client jogo-acoes --validity-days 0" (or a negative or non-integer value)
  When the command is executed
  Then it fails with a usage-error exit code
  And no row is persisted
```

## Requisitos funcionais

- FR1: O comando `generate` exige um identificador de cliente (`--client`), não vazio.
- FR2: A chave gerada tem entropia criptográfica suficiente (mínimo 256 bits / 32 bytes
  aleatórios).
- FR3: A chave gerada tem um prefixo identificador (`dak_`, ver `plan.md`) para ser
  reconhecível em logs e em scanners de segredo.
- FR4: Apenas o hash da chave é persistido — a chave em texto puro nunca chega ao banco de
  dados.
- FR5: A chave em texto puro é exibida ao operador exatamente uma vez, no momento da geração.
  Não existe comando ou consulta que a recupere depois desse momento.
- FR6: Cada linha persistida registra: o hash, o nome do cliente dono da chave, e a data/hora
  de criação (UTC).
- FR7: O hash usa HMAC-SHA256 com uma chave secreta (pepper) mantida fora do banco de dados
  e fora do código-fonte (variável de ambiente — ver `plan.md`).
- FR8: O comando `generate` aceita um argumento opcional `--validity-days <N>` definindo por
  quantos dias, a partir da criação, a chave é válida.
- FR9: Quando `--validity-days` não é informado, a chave não tem prazo de validade
  (indeterminada) — o campo de validade fica nulo.
- FR10: Quando informado, `--validity-days` deve ser um número inteiro positivo (> 0);
  qualquer outro valor falha com erro de uso, sem persistir nada.
- FR11: Esta feature só calcula e grava o prazo de validade — checar se uma chave já expirou
  no momento do uso é responsabilidade da futura biblioteca de leitura (fora de escopo aqui).

## Requisitos não-funcionais

- A chave em texto puro nunca aparece em log de aplicação, nem completa nem parcialmente
  (nem em nível debug).
- Se o pepper do HMAC não estiver configurado, o comando falha de forma clara antes de gerar
  qualquer chave — nunca falha silenciosamente nem gera uma chave sem hash.

## Fora de escopo

- Verificar/validar uma chave existente (feature futura separada).
- A biblioteca de leitura usada pelo serviço protegido (esta mesma instância) para validar
  chaves recebidas de clientes (feature futura separada — este projeto só emite).
- Revogar uma chave antes do prazo de validade, ou rotacioná-la automaticamente.
- Checar, em tempo de uso, se uma chave já passou do prazo de validade (fica com a futura
  biblioteca de leitura — ver FR11).
- Interface de administração (decisão já registrada no README do projeto).
- Listar ou auditar chaves já emitidas via CLI.

## Critérios de aceite

Cobertos pelos cenários acima — uma chave gerada com sucesso produz uma linha no banco com
hash+cliente+timestamp e imprime a chave em texto puro uma única vez; qualquer entrada
inválida ou pré-condição ausente (cliente faltando, pepper ausente) falha sem persistir nada
e sem imprimir uma chave.

## Decisões em aberto

- O nome do cliente aceita qualquer string não vazia na primeira versão (sem lista
  pré-cadastrada de clientes válidos), já que não há interface de administração ainda — a
  confirmar se isso é aceitável ou se deveria validar contra uma lista fixa.
