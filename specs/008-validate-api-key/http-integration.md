# Guia de integração HTTP: validate-api-key

Documentação, não código desta biblioteca — ver `plan.md`, decisão "Integração HTTP pronta".
`api-key-validation` não depende de servlet/Spring MVC; este guia mostra como um serviço
protegido (Spring Boot com `spring-boot-starter-web`) pode ligar `ApiKeyValidator` às
requisições que recebe. Os trechos são ponto de partida para copiar e adaptar, não uma API
mantida — quando houver um consumidor real, a integração escolhida pode virar spec própria.

## Pré-requisitos no serviço consumidor

1. Dependência em `api-key-validation` (traz `api-key-core` transitivamente) e
   `spring-boot-starter-web`.
2. Registrar os beans e a entidade — a biblioteca ainda não tem auto-configuração, e o pacote
   base do consumidor normalmente não cobre `dev.leilaalgarve.apikey`:

   ```java
   import org.springframework.boot.persistence.autoconfigure.EntityScan; // Spring Boot 4
   import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

   @SpringBootApplication(scanBasePackages = {"com.example.mail",
           "dev.leilaalgarve.apikey.validation", "dev.leilaalgarve.apikey.core"})
   @EntityScan("dev.leilaalgarve.apikey.core")
   @EnableJpaRepositories("dev.leilaalgarve.apikey.core")
   public class MailServiceApplication { ... }
   ```

   `@EntityScan` e `@EnableJpaRepositories` **substituem** a varredura padrão (o pacote da
   aplicação) em vez de somar a ela: se o serviço tiver entidades/repositórios JPA próprios,
   liste os pacotes dele também (ex.: `@EntityScan({"com.example.mail",
   "dev.leilaalgarve.apikey.core"})`). Esta configuração exata é a que os testes de
   `api-key-validation` usam para subir (`example.consumer.ConsumerTestApplication`), então
   ela é verificada a cada `mvn verify`.

3. Mesmo banco populado pela CLI e a mesma variável `API_KEY_HMAC_PEPPER` — com outro pepper,
   todo hash calculado diverge e toda chave volta `NOT_FOUND`.
4. Escolher o header que carrega a chave. Os exemplos usam `X-API-Key`; a biblioteca não
   impõe nenhum.

## Política de resposta (decisão do consumidor)

A biblioteca sempre devolve o motivo exato (`spec.md` FR7). O que chega ao chamador externo é
decisão de cada serviço. Sugestão de partida:

| Motivo | Status | Corpo para API pública | Corpo para API interna/confiável |
|---|---|---|---|
| `MISSING` | 401 | genérico ("API key inválida ou ausente") | código do motivo |
| `MALFORMED` | 401 | genérico | código do motivo |
| `NOT_FOUND` | 401 | genérico | código do motivo |
| `REVOKED` | 401 | genérico | código do motivo |
| `EXPIRED` | 401 | genérico | código do motivo |

- Numa API pública, responder igual para todos os motivos evita enumeração de chaves (ver
  `plan.md`, "Riscos e trade-offs"): sem isso, quem testa uma chave vazada descobre se ela já
  existiu (`REVOKED`/`EXPIRED`) ou nunca existiu (`NOT_FOUND`).
- O motivo exato continua útil **no log do próprio serviço**, para investigação. Nunca logar a
  chave em texto puro — mesmo princípio de `spec.md` FR6.
- Limite de requisições (`429`) não é motivo de validação de chave (ver `plan.md`, decisão
  `RATE_LIMITED`) — se o serviço precisar, fica numa camada própria, antes ou depois desta.

## Alternativa A: `OncePerRequestFilter`

O filtro roda antes do `DispatcherServlet` e responde ele mesmo quando a chave é rejeitada.

```java
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";
    static final String CLIENT_NAME_ATTRIBUTE = "apiKey.clientName";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final ApiKeyValidator validator;

    public ApiKeyFilter(ApiKeyValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        switch (validator.validate(request.getHeader(HEADER))) {
            case ApiKeyValidationResult.Valid valid -> {
                request.setAttribute(CLIENT_NAME_ATTRIBUTE, valid.clientName());
                chain.doFilter(request, response);
            }
            case ApiKeyValidationResult.Invalid invalid -> {
                // Reason goes to our own log only -- never the raw key.
                log.info("API key rejected: {} {}", invalid.reason(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write(
                        "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Invalid or missing API key\"}");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
}
```

- **Quando escolher**: proteger tudo o que chega ao servlet container, inclusive caminhos que
  não são controllers (recursos estáticos, endpoints inexistentes — que respondem 401, não
  404, sem revelar quais rotas existem).
- **Limitação**: exceções lançadas num filtro **não** passam por `@RestControllerAdvice` (o
  filtro roda fora do `DispatcherServlet`). Por isso o filtro escreve a resposta sozinho, e o
  formato do erro fica duplicado em relação ao resto da API, se ela já tiver um padrão de erro
  via advice.
- Se o serviço já usa Spring Security, o equivalente idiomático é um filtro na
  `SecurityFilterChain` que monta um `Authentication` com `clientName` — mesma limitação.

## Alternativa B: `HandlerInterceptor` + `@RestControllerAdvice`

A validação lança uma exceção; o `@RestControllerAdvice` a converte em resposta, junto com o
resto do tratamento de erros da API. Para isso funcionar, a validação precisa rodar **dentro**
do `DispatcherServlet` — num `HandlerInterceptor`, não num filtro.

```java
public class ApiKeyRejectedException extends RuntimeException {

    private final ApiKeyFailureReason reason;

    public ApiKeyRejectedException(ApiKeyFailureReason reason) {
        super("API key rejected: " + reason);
        this.reason = reason;
    }

    public ApiKeyFailureReason reason() {
        return reason;
    }
}
```

```java
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-API-Key";
    static final String CLIENT_NAME_ATTRIBUTE = "apiKey.clientName";

    private final ApiKeyValidator validator;

    public ApiKeyInterceptor(ApiKeyValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        switch (validator.validate(request.getHeader(HEADER))) {
            case ApiKeyValidationResult.Valid valid ->
                    request.setAttribute(CLIENT_NAME_ATTRIBUTE, valid.clientName());
            case ApiKeyValidationResult.Invalid invalid ->
                    throw new ApiKeyRejectedException(invalid.reason());
        }
        return true;
    }
}
```

```java
@Configuration
public class ApiKeyWebConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor interceptor;

    public ApiKeyWebConfig(ApiKeyInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**");
    }
}
```

```java
@RestControllerAdvice
public class ApiKeyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyExceptionHandler.class);

    @ExceptionHandler(ApiKeyRejectedException.class)
    public ProblemDetail handle(ApiKeyRejectedException e, HttpServletRequest request) {
        log.info("API key rejected: {} {}", e.reason(), request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
        // Internal/trusted APIs may choose to expose the exact reason instead:
        // problem.setProperty("reason", e.reason().name());
        return problem;
    }
}
```

- **Quando escolher**: a API já centraliza erros num `@RestControllerAdvice` e o formato da
  resposta de chave rejeitada deve ser igual ao dos outros erros (`ProblemDetail`, RFC 9457).
  Também facilita aplicar a checagem só a parte das rotas (`addPathPatterns`/
  `excludePathPatterns`).
- **Limitação**: o interceptor só roda quando existe um handler mapeado. Uma rota inexistente
  responde 404 sem passar pela checagem da chave — revela quais rotas existem a quem não tem
  chave. Aceitável para a maioria das APIs internas; para uma API pública sensível a isso, a
  Alternativa A é mais adequada.
- Variação: em vez do interceptor, um `HandlerMethodArgumentResolver` para um parâmetro
  anotado (ex.: `@ApiClient String clientName`) lança a mesma exceção — útil quando só alguns
  endpoints exigem chave e se quer isso explícito na assinatura do método.

## Lendo o cliente autenticado no controller

Nas duas alternativas, o nome do cliente fica num atributo da requisição:

```java
@PostMapping("/api/mail")
public ResponseEntity<Void> send(@RequestAttribute("apiKey.clientName") String clientName,
        @RequestBody MailRequest body) { ... }
```

`clientName` é o mesmo valor passado em `--client` ao gerar a chave pela CLI
(`contracts/validation-api.md`), útil para log, auditoria ou autorização por cliente.
