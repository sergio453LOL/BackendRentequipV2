package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.notifications.SmsGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two-step verification end to end: turning it on by SMS, a login that stops at the code, wrong and
 * reused codes, and turning it off again. The SMS gateway is mocked so the test can read the code.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TwoFactorLoginTest {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SmsGatewayClient smsGatewayClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String email;
    private String phone;

    @BeforeEach
    void registerUser() throws Exception {
        String id = String.valueOf(System.nanoTime()).substring(0, 11);
        email = "twofactor" + id + "@test.pe";
        phone = "9" + id.substring(3, 11);
    }

    @Test
    void aProtectedLoginNeedsTheCodeSentBySms() throws Exception {
        String token = register();

        // Turning it on: a code goes to the phone and only the right one activates the protection.
        JsonNode setup = json(call("/api/v1/users/me/two-factor/setup", token, "{\"channel\":\"SMS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("SMS")));
        String setupCode = lastSmsCode();
        String wrongSetup = setupCode.equals("000000") ? "111111" : "000000";
        call("/api/v1/users/me/two-factor/confirm", token, verifyBody(setup, wrongSetup))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        call("/api/v1/users/me/two-factor/confirm", token, verifyBody(setup, setupCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorChannel").value("SMS"));

        // The password alone no longer hands out tokens.
        JsonNode challenge = json(login().andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.destination").value(org.hamcrest.Matchers.endsWith(phone.substring(6)))));
        String loginCode = lastSmsCode();
        String wrongLogin = loginCode.equals("000000") ? "111111" : "000000";

        call("/api/v1/auth/login/verify", null, verifyBody(challenge, wrongLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4 intentos")));
        call("/api/v1/auth/login/verify", null, verifyBody(challenge, loginCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));

        // A code works once.
        call("/api/v1/auth/login/verify", null, verifyBody(challenge, loginCode))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    void turningItOffNeedsThePasswordAndRestoresTheNormalLogin() throws Exception {
        String token = register();
        JsonNode setup = json(call("/api/v1/users/me/two-factor/setup", token, "{\"channel\":\"SMS\"}")
                .andExpect(status().isOk()));
        call("/api/v1/users/me/two-factor/confirm", token, verifyBody(setup, lastSmsCode()))
                .andExpect(status().isOk());

        call("/api/v1/users/me/two-factor/disable", token, "{\"password\":\"Wrong12345\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_WRONG_PASSWORD"));
        call("/api/v1/users/me/two-factor/disable", token, "{\"password\":\"Password123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorChannel").doesNotExist());

        login().andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aNewCodeCannotBeRequestedRightAway() throws Exception {
        String token = register();
        call("/api/v1/users/me/two-factor/setup", token, "{\"channel\":\"SMS\"}").andExpect(status().isOk());

        call("/api/v1/users/me/two-factor/setup", token, "{\"channel\":\"SMS\"}")
                .andExpect(status().isTooManyRequests());
    }

    private String register() throws Exception {
        String payload = """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"%s","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"Password123","phone":"%s"}"""
                .formatted(email, email.substring(9, 20), email, email, phone);
        JsonNode auth = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return auth.get("accessToken").asText();
    }

    private ResultActions login() throws Exception {
        return call("/api/v1/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"Password123\"}".formatted(email));
    }

    private ResultActions call(String url, String token, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String verifyBody(JsonNode challenge, String code) {
        return "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge.get("challengeId").asText(), code);
    }

    /** Reads the code out of the last SMS handed to the gateway. */
    private String lastSmsCode() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(smsGatewayClient, org.mockito.Mockito.atLeastOnce()).send(eq("+51" + phone), text.capture());
        Matcher matcher = CODE.matcher(text.getValue());
        assertThat(matcher.find()).isTrue();
        clearInvocations(smsGatewayClient);
        return matcher.group(1);
    }
}
