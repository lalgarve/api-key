package io.deployo.apikey.issuance;

import org.springframework.stereotype.Component;

/** The real, production {@link ProcessExiter} -- actually terminates the JVM. */
@Component
public class SystemProcessExiter implements ProcessExiter {

    @Override
    public void exit(int code) {
        System.exit(code);
    }
}
