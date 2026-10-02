package dev.leilaalgarve.apikey.cucumber;

import dev.leilaalgarve.apikey.DeployoApiKeyApplication;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Ties Cucumber to one shared Spring context for the whole suite (cucumber-spring's model --
 * unlike a JUnit @SpringBootTest class, there's no per-scenario @Transactional rollback here,
 * see ScenarioState). The pepper is fixed for every scenario except the one that needs it
 * missing -- see GenerateApiKeySteps, which builds its own GenerateCommand with a blank-pepper
 * ApiKeyHasher for just that scenario instead of using the injected bean.
 *
 * <p>The repository is a spy (not a plain bean) so CommandDispatchSteps can simulate a
 * persistence failure for exactly one scenario ("the database rejects..."), same reasoning as
 * GenerateCommandRotationAtomicityTest -- a spy wraps the real repository, so every other
 * scenario's calls still hit the real H2 database. CucumberHooks resets the spy's stubbing
 * after every scenario so it never leaks into the next one.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = DeployoApiKeyApplication.class, properties = "API_KEY_HMAC_PEPPER=test-pepper")
public class CucumberSpringConfiguration {

    @MockitoSpyBean
    private ApiKeyRepository repository;
}
