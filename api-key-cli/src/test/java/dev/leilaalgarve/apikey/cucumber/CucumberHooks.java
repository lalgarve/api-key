package dev.leilaalgarve.apikey.cucumber;

import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/** Suite-wide setup/cleanup that must run around every scenario, regardless of which feature it's in. */
public class CucumberHooks {

    @Autowired
    private ApiKeyRepository repository;

    @Autowired
    private ScenarioState state;

    @Before
    public void resetScenarioState() {
        state.reset();
    }

    /**
     * Clears any doThrow(...) stubbing a scenario registered on the shared repository spy
     * (see CucumberSpringConfiguration) -- Mockito.reset() only removes explicit stubbing and
     * interaction history, not a spy's fundamental "delegate to the real object" behavior, so
     * this restores normal pass-through persistence for the next scenario without needing a
     * fresh Spring context per scenario.
     *
     * Then deletes every key the scenario created (by the suffixed client names ScenarioState
     * handed out): scenarios commit for real, and the suite runs against a Postgres that
     * persists between runs, so leftovers would otherwise accumulate and leak into later
     * tests. Reset first, so a scenario's stubbing can never block its own cleanup.
     */
    @After
    public void resetRepositorySpyAndDeleteScenarioKeys() {
        Mockito.reset(repository);
        for (String client : state.qualifiedClients()) {
            repository.deleteAll(repository.findByClientName(client));
        }
    }
}
