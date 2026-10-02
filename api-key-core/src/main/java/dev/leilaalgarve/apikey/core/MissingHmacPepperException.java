package dev.leilaalgarve.apikey.core;

/** Thrown when {@code API_KEY_HMAC_PEPPER} is not configured -- see plan.md, "Onde fica o pepper do HMAC". */
public class MissingHmacPepperException extends RuntimeException {

    public MissingHmacPepperException() {
        super("API_KEY_HMAC_PEPPER is not configured");
    }
}
