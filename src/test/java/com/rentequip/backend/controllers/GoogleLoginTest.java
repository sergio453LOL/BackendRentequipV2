package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.exceptions.GoogleSignInException;
import com.rentequip.backend.services.EmailService;
import com.rentequip.backend.security.GoogleIdTokenVerifier;
import com.rentequip.backend.security.GoogleIdTokenVerifier.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Continuar con Google": an unknown email is sent to sign up, the sign-up takes the email from the Google
 * token and not from the form, and an existing account (made with a password too) signs in. The verifier is
 * mocked: what it checks against Google's keys is Spring Security's code, not ours.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginTest {

    private static final String CREDENTIAL = "header.payload.signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    /** Captures the codes that would go out by email (the tests never talk to an SMTP server). */
    @MockitoBean
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String email;
    private String taxId;

    @BeforeEach
    void googleAccount() {
        String id = String.valueOf(System.nanoTime()).substring(0, 11);
        email = "google" + id + "@gmail.com";
        taxId = id;
        when(googleIdTokenVerifier.verify(CREDENTIAL)).thenReturn(new GoogleIdentity(email, "Ana", "Paz"));
    }

    @Test
    void anUnknownGoogleEmailIsSentToSignUpAndThenSignsIn() throws Exception {
        googleLogin()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_NOT_REGISTERED"));

        // The form says another email: the one Google verified wins.
        post("/api/v1/auth/google/register", """
                {"credential":"%s","company":{"name":"Empresa %s","taxId":"%s","email":"empresa%s@test.pe",
                  "city":"Lima","latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"otro@test.pe"}"""
                .formatted(CREDENTIAL, taxId, taxId, taxId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        googleLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));

        // A second sign-up with the same Google account is refused.
        post("/api/v1/auth/google/register", """
                {"credential":"%s","company":{"name":"Otra %s","taxId":"%s","email":"otra%s@test.pe",
                  "city":"Lima","latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz"}"""
                .formatted(CREDENTIAL, taxId, taxId, taxId))
                .andExpect(status().isConflict());
    }

    @Test
    void anAccountMadeWithAPasswordAlsoSignsInWithGoogle() throws Exception {
        post("/api/v1/auth/register", """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"empresa%s@test.pe","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"Password123"}"""
                .formatted(taxId, taxId, taxId, email.toUpperCase()))
                .andExpect(status().isCreated());

        googleLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void aRejectedTokenSignsNobodyIn() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenThrow(GoogleSignInException.invalidToken());

        googleLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void aMissingCredentialIsAValidationError() throws Exception {
        post("/api/v1/auth/google", "{\"credential\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aGoogleAccountSetsItsFirstPasswordWithACodeSentToItsEmail() throws Exception {
        String token = registerWithGoogle();
        authed("GET", "/api/v1/users/me", token, null).andExpect(jsonPath("$.passwordSet").value(false));

        // It has no current password to give, so without a code nothing changes (400: the session stays open).
        authed("POST", "/api/v1/users/me/password", token, "{\"newPassword\":\"NuevaClave2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));

        String challengeId = json(authed("POST", "/api/v1/users/me/account-code", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"))).get("challengeId").asText();
        String code = lastEmailedCode();
        String wrong = "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);

        authed("POST", "/api/v1/users/me/password", token, passwordWithCode(challengeId, wrong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        authed("POST", "/api/v1/users/me/password", token, passwordWithCode(challengeId, code))
                .andExpect(status().isNoContent());

        authed("GET", "/api/v1/users/me", token, null).andExpect(jsonPath("$.passwordSet").value(true));
        post("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"NuevaClave2026\"}".formatted(email))
                .andExpect(status().isOk());
        // From now on it uses its password like any other account.
        authed("POST", "/api/v1/users/me/account-code", token, null).andExpect(status().isBadRequest());
    }

    @Test
    void aGoogleAccountTurnsOffTheTwoStepVerificationWithAnEmailCode() throws Exception {
        String token = registerWithGoogle();
        String setup = json(authed("POST", "/api/v1/users/me/two-factor/setup", token, "{\"channel\":\"EMAIL\"}")
                .andExpect(status().isOk())).get("challengeId").asText();
        authed("POST", "/api/v1/users/me/two-factor/confirm", token,
                "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(setup, lastEmailedCode()))
                .andExpect(jsonPath("$.twoFactorChannel").value("EMAIL"));

        // A password proves nothing for an account that never had one.
        authed("POST", "/api/v1/users/me/two-factor/disable", token, "{\"password\":\"Cualquiera123\"}")
                .andExpect(status().isBadRequest());

        String challengeId = json(authed("POST", "/api/v1/users/me/account-code", token, null)).get("challengeId").asText();
        authed("POST", "/api/v1/users/me/two-factor/disable", token,
                "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challengeId, lastEmailedCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorChannel").doesNotExist());
    }

    private String registerWithGoogle() throws Exception {
        return json(post("/api/v1/auth/google/register", """
                {"credential":"%s","company":{"name":"Empresa %s","taxId":"%s","email":"empresa%s@test.pe",
                  "city":"Lima","latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz"}"""
                .formatted(CREDENTIAL, taxId, taxId, taxId))
                .andExpect(status().isCreated())).get("accessToken").asText();
    }

    private static String passwordWithCode(String challengeId, String code) {
        return "{\"newPassword\":\"NuevaClave2026\",\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challengeId, code);
    }

    @SuppressWarnings("unchecked")
    private String lastEmailedCode() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService, atLeastOnce()).sendOrFail(eq(email), anyString(), eq("verification-code"), variables.capture());
        return String.valueOf(variables.getValue().get("code"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions authed(String method, String url, String token, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(org.springframework.http.HttpMethod.valueOf(method), url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions googleLogin() throws Exception {
        return post("/api/v1/auth/google", "{\"credential\":\"%s\"}".formatted(CREDENTIAL));
    }

    private ResultActions post(String url, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
