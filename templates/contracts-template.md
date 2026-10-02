# Contrato de CLI: <nome da feature>

Escrito antes da implementação — a implementação segue o contrato. Equivalente a um contrato
OpenAPI, mas para a interface de linha de comando.

## Comando: `<comando>`

**Uso:**
```
api-key <comando> [opções]
```

**Argumentos:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--<flag>` | sim/não | <descrição> |

**Saída (stdout), sucesso:**
```
<exemplo de saída>
```

**Exit codes:**

| Código | Significado |
|---|---|
| 0 | Sucesso |
| 1 | <erro esperado> |

**Erros (stderr):**

| Condição | Mensagem | Exit code |
|---|---|---|
| <condição> | <mensagem> | <código> |
