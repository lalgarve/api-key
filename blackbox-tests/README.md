# Testes caixa-preta da CLI

Suíte de aceite em Python (BDD com `behave`) que roda o jar empacotado da CLI como um processo
separado e confere só exit code, stdout e stderr. Spec, plano e tarefas em
[`specs/012-blackbox-cli-tests/`](../specs/012-blackbox-cli-tests/).

## Como rodar

Pré-requisitos: Java 21, Python 3.11 ou mais novo e PostgreSQL de pé (o
`docker compose up -d --wait db` da raiz, ou o Postgres nativo do ambiente sandbox).

```
mvn package -DskipTests
blackbox-tests/run.sh
```

`run.sh` cria um `.venv` nesta pasta na primeira vez, instala `requirements.txt`, roda os testes
unitários do pacote `blackbox/` (pytest) e depois os cenários. Argumentos extras vão para o
`behave` (por exemplo, `blackbox-tests/run.sh features/revoke.feature`).

- `--include-pending` (primeiro argumento) roda também os cenários marcados `@pending`, de specs
  ainda não implementadas (hoje `010` e `011`).
- `API_KEY_CLI_JAR` aponta para outro jar; sem ela, a suíte usa o único
  `api-key-cli/target/api-key-*.jar`.
- `SPRING_PROFILES_ACTIVE` e `SPRING_DATASOURCE_*` são repassadas ao processo da CLI; nenhuma
  outra variável do seu ambiente chega a ele.

No GitHub, a suíte roda só quando disparada: aba Actions, workflow "Black-box CLI tests", "Run
workflow".

Cada comando da CLI sobe o Spring, então a suíte inteira leva alguns minutos (cerca de 8 no
ambiente sandbox).

## Escrevendo cenários

Nos passos `When I run "..."` e nos textos esperados:

- `{acme}` vira o nome de cliente deste cenário (`acme-<sufixo aleatório>`), para nenhum cenário
  ver chaves de outro;
- `{id:k1}` vira o `id` da chave com rótulo `k1`, lido da saída de `list`.

Todo estado é montado pela própria CLI (`generate`, `revoke`, opções de relógio), nunca pelo
banco. A chave em texto puro fica só em memória, e os relatórios de falha a mostram mascarada
(`dak_****`).
