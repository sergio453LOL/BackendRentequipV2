package com.rentequip.backend.notifications;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/** The SMS gateway receives the number, the text, the SIMs and the API key the way its API documents them. */
class SmsGatewayClientTest {

    private static final String BASE_URL = "https://sms.example.test";

    @Test
    void sendsTheMessageWithTheBearerKeyAndTheConfiguredSims() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SmsGatewayClient client = new SmsGatewayClient(builder, BASE_URL, "secret-key", "3, 4");

        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/messages/send")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(queryParam("type", "SMS"))
                .andExpect(queryParam("mobile_numbers%5B%5D", "%2B51987654321"))
                .andExpect(queryParam("sims%5B%5D", "3", "4"))
                .andRespond(withSuccess("{\"id\":54}", org.springframework.http.MediaType.APPLICATION_JSON));

        client.send("+51987654321", "Hola mundo");

        server.verify();
    }

    @Test
    void reportsARejectedKeyAsADeliveryFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SmsGatewayClient client = new SmsGatewayClient(builder, BASE_URL, "wrong", "");
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        NotificationDeliveryException error = assertThrows(NotificationDeliveryException.class,
                () -> client.send("+51987654321", "Hola"));

        assertThat(error.getMessage()).contains("API key");
    }

    @Test
    void withoutUrlOrKeyItAnswersThatSmsIsNotConfigured() {
        SmsGatewayClient client = new SmsGatewayClient(RestClient.builder(), "", "", "");

        assertThat(client.isConfigured()).isFalse();
        assertThrows(NotificationDeliveryException.class, () -> client.send("+51987654321", "Hola"));
    }
}
