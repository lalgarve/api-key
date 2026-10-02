package example.consumer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boots the tests the way a protected service would: from its own package, outside
 * dev.leilaalgarve.apikey, wiring the library in with exactly the annotations
 * specs/008-validate-api-key/http-integration.md tells consumers to add. If that guide's
 * setup stops working, these tests stop booting.
 */
@SpringBootApplication(scanBasePackages = {"example.consumer",
        "dev.leilaalgarve.apikey.validation", "dev.leilaalgarve.apikey.core"})
@EntityScan("dev.leilaalgarve.apikey.core")
@EnableJpaRepositories("dev.leilaalgarve.apikey.core")
public class ConsumerTestApplication {
}
