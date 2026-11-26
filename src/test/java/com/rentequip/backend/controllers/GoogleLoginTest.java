package com.rentequip.backend.controllers;

import com.rentequip.backend.exceptions.GoogleSignInException;
import com.rentequip.backend.security.GoogleIdTokenVerifier;
import com.rentequip.backend.security.GoogleIdTokenVerifier.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
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

    private ResultActions googleLogin() throws Exception {
        return post("/api/v1/auth/google", "{\"credential\":\"%s\"}".formatted(CREDENTIAL));
    }

    private ResultActions post(String url, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
