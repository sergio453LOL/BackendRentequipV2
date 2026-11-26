package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.RecaptchaException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Google's siteverify is played by a local HTTP server: the verdict decides, a missing token only counts
 * when it is required, and an unreachable Google does not lock anyone out.
 */
class RecaptchaVerifierTest {

    private HttpServer google;
    private final AtomicReference<String> verdict = new AtomicReference<>();
    private final AtomicReference<String> lastForm = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (google != null) {
            google.stop(0);
        }
    }

    @Test
    void aPersonPasses() throws Exception {
        verdict.set("{\"success\":true,\"score\":0.9,\"action\":\"login\"}");

        assertThatCode(() -> verifier(false).check("token-123", "login")).doesNotThrowAnyException();
        assertThat(lastForm.get()).contains("secret=test-secret", "response=token-123");
    }

    @Test
    void aLowScoreIsTurnedAway() throws Exception {
        verdict.set("{\"success\":true,\"score\":0.1,\"action\":\"login\"}");

        assertThatThrownBy(() -> verifier(false).check("token", "login")).isInstanceOf(RecaptchaException.class);
    }

    @Test
    void aTokenForAnotherActionOrAnInvalidOneIsTurnedAway() throws Exception {
        verdict.set("{\"success\":true,\"score\":0.9,\"action\":\"signup\"}");
        assertThatThrownBy(() -> verifier(false).check("token", "login")).isInstanceOf(RecaptchaException.class);

        verdict.set("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}");
        assertThatThrownBy(() -> verifier(false).check("token", "login")).isInstanceOf(RecaptchaException.class);
    }

    @Test
    void aMissingTokenOnlyFailsWhenRequired() throws Exception {
        assertThatCode(() -> verifier(false).check(null, "login")).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier(true).check(" ", "login")).isInstanceOf(RecaptchaException.class);
    }

    @Test
    void withoutASecretNothingIsChecked() {
        RecaptchaVerifier off = new RecaptchaVerifier("", 0.5, true, "http://localhost:1/siteverify");

        assertThatCode(() -> off.check(null, "login")).doesNotThrowAnyException();
    }

    @Test
    void anUnreachableGoogleLetsTheLoginThrough() {
        RecaptchaVerifier unreachable = new RecaptchaVerifier("test-secret", 0.5, true, "http://localhost:1/siteverify");

        assertThatCode(() -> unreachable.check("token", "login")).doesNotThrowAnyException();
    }

    private RecaptchaVerifier verifier(boolean required) throws Exception {
        if (google == null) {
            google = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            google.createContext("/siteverify", exchange -> {
                lastForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = verdict.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            google.start();
        }
        return new RecaptchaVerifier("test-secret", 0.5, required,
                "http://localhost:" + google.getAddress().getPort() + "/siteverify");
    }
}
