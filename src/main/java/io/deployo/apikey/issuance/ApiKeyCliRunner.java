package io.deployo.apikey.issuance;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Thin adapter between Spring Boot's application startup and {@link GenerateCommand}: runs
 * the command against the real stdout/stderr, and exits the process with the returned code
 * when it's non-zero. Success (0) never calls {@link ProcessExiter#exit(int)} -- the JVM ends
 * normally on its own, which is what keeps DeployoApiKeyApplicationTests' direct call to
 * {@code main()} safe (it exercises the success path).
 */
@Component
public class ApiKeyCliRunner implements CommandLineRunner {

    private final GenerateCommand command;
    private final ProcessExiter exiter;

    public ApiKeyCliRunner(GenerateCommand command, ProcessExiter exiter) {
        this.command = command;
        this.exiter = exiter;
    }

    @Override
    public void run(String... args) {
        int exitCode = command.execute(args, System.out, System.err);
        if (exitCode != 0) {
            exiter.exit(exitCode);
        }
    }
}
