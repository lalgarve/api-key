package dev.leilaalgarve.apikey.cucumber;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-scenario state shared across step-definition classes. A plain singleton Spring bean,
 * reset by CucumberHooks' @Before hook at the start of every scenario -- not @ScenarioScope:
 * that relies on a Spring scoped-proxy whose target didn't reliably resolve to the same
 * instance across step-definition classes in practice (fields read back null). Cucumber runs
 * scenarios sequentially in one thread by default, so a reset-each-@Before singleton is just
 * as safe and far simpler to reason about.
 *
 * <p>Two jobs: 1. maps a scenario's human-friendly key label ("id 3", "id 5" -- mirroring the
 * ids used in specs/*-/spec.md's prose Gherkin) to the real, auto-generated database id, since
 * the suite's H2 instance is shared across the whole run and ids keep climbing -- scenarios
 * never get to assume a literal id. 2. appends a random suffix to every client name a
 * scenario touches, so two scenarios that both say "jogo-acoes" never see each other's rows --
 * same isolation purpose a real per-scenario transaction rollback would give, without needing
 * one (cucumber-spring doesn't wrap each scenario in a rolled-back transaction the way
 * @Transactional does for a JUnit test).
 */
@Component
public class ScenarioState {

    private String suffix;
    private Map<String, Long> idsByLabel;

    public ByteArrayOutputStream outBytes;
    public ByteArrayOutputStream errBytes;
    public int exitCode;
    public boolean hmacPepperMissing;
    /** The last client name a "When" step referenced, in its original (un-suffixed) form. */
    public String lastClient;

    public ScenarioState() {
        reset();
    }

    public void reset() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        idsByLabel = new HashMap<>();
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        exitCode = 0;
        hmacPepperMissing = false;
        lastClient = null;
    }

    public String qualifyClient(String client) {
        return client + "-" + suffix;
    }

    public void rememberId(String label, Long realId) {
        idsByLabel.put(label, realId);
    }

    /** Returns the real id for a remembered label, or the literal token itself if never seen. */
    public String resolveId(String labelOrLiteralId) {
        Long real = idsByLabel.get(labelOrLiteralId);
        return real == null ? labelOrLiteralId : String.valueOf(real);
    }
}
