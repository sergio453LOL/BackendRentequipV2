package com.rentequip.backend.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** RUC check digit, and the SUNAT/RENIEC answers read in both the current and the older field names. */
class IdentityLookupTest {

    private static final String BASE_URL = "https://identity.example.test";

    @Test
    void acceptsRealRucsAndRejectsTypos() {
        assertThat(RucValidator.isValid("20131312955")).isTrue();  // SUNAT
        assertThat(RucValidator.isValid("20100047218")).isTrue();  // BCP
        assertThat(RucValidator.isValid("20100047219")).isFalse(); // last digit changed
        assertThat(RucValidator.isValid("30100047218")).isFalse(); // no such prefix
        assertThat(RucValidator.isValid("2010004721")).isFalse();  // ten digits
        assertThat(RucValidator.typeOf("20100047218")).isEqualTo(RucValidator.RucType.LEGAL_ENTITY);
        assertThat(RucValidator.typeOf("10456789012")).isEqualTo(RucValidator.RucType.NATURAL_PERSON);
    }

    @Test
    void readsACompanyInGoodStanding() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(builder, BASE_URL, "token-123");
        server.expect(requestTo(startsWith(BASE_URL + "/v1/sunat/ruc")))
                .andExpect(queryParam("numero", "20131312955"))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess("""
                        {"razon_social":"SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA",
                         "numero_documento":"20131312955","estado":"ACTIVO","condicion":"HABIDO",
                         "direccion":"AV. GARCILASO DE LA VEGA NRO. 1472"}""", MediaType.APPLICATION_JSON));

        RucInfo info = client.findRuc("20131312955").orElseThrow();

        assertThat(info.name()).startsWith("SUPERINTENDENCIA NACIONAL");
        assertThat(info.inGoodStanding()).isTrue();
        assertThat(info.type()).isEqualTo(RucValidator.RucType.LEGAL_ENTITY);
    }

    @Test
    void readsTheOlderApisNetPeFieldsForADni() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(builder, BASE_URL, "token-123");
        server.expect(requestTo(startsWith(BASE_URL + "/v1/reniec/dni")))
                .andRespond(withSuccess("""
                        {"nombres":"ANA MARIA","apellidoPaterno":"PAZ","apellidoMaterno":"ROJAS"}""",
                        MediaType.APPLICATION_JSON));

        DniInfo info = client.findDni("46027896").orElseThrow();

        assertThat(info.firstNames()).isEqualTo("ANA MARIA");
        assertThat(info.lastNames()).isEqualTo("PAZ ROJAS");
        assertThat(info.fullName()).isEqualTo("ANA MARIA PAZ ROJAS");
    }

    @Test
    void anUnknownNumberIsEmptyNotAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(builder, BASE_URL, "token-123");
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findRuc("20100047218")).isEqualTo(Optional.empty());
    }
    @Test
    void withoutATokenUsesTheFreeEndpointAndReadsTheLocation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(builder, BASE_URL, "");
        server.expect(requestTo(startsWith(BASE_URL + "/v1/ruc")))
                .andExpect(queryParam("numero", "20131312955"))
                .andRespond(withSuccess("""
                        {"nombre":"SUNAT","numeroDocumento":"20131312955","estado":"ACTIVO","condicion":"HABIDO",
                         "direccion":"AV. GARCILASO DE LA VEGA NRO 1472","distrito":"LIMA","provincia":"LIMA",
                         "departamento":"LIMA"}""", MediaType.APPLICATION_JSON));

        RucInfo info = client.findRuc("20131312955").orElseThrow();

        assertThat(client.isConfigured()).isTrue();
        assertThat(info.name()).isEqualTo("SUNAT");
        assertThat(info.department()).isEqualTo("LIMA");
        server.verify();
    }

    @Test
    void readsApisperuAndTreatsADashAddressAsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(List.of(
                IdentityLookupClient.provider(IdentityLookupClient.Kind.APISPERU, builder, BASE_URL, "tok-1")));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/ruc/10467793549")))
                .andExpect(queryParam("token", "tok-1"))
                .andRespond(withSuccess("""
                        {"ruc":"10467793549","razonSocial":"FERIA VILA CARLOS ESTEBAN","nombreComercial":"-","telefonos":[],
                         "estado":"ACTIVO","condicion":"HABIDO","direccion":"-","departamento":"LIMA","provincia":"LIMA",
                         "distrito":"MIRAFLORES","ubigeo":"150122","capital":"LIMA"}""", MediaType.APPLICATION_JSON));

        RucInfo info = client.findRuc("10467793549").orElseThrow();

        assertThat(info.name()).isEqualTo("FERIA VILA CARLOS ESTEBAN");
        assertThat(info.address()).isNull();
        assertThat(info.type()).isEqualTo(RucValidator.RucType.NATURAL_PERSON);
        server.verify();
    }

    @Test
    void apisperuSaysNotFoundWithSuccessFalse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityLookupClient client = new IdentityLookupClient(List.of(
                IdentityLookupClient.provider(IdentityLookupClient.Kind.APISPERU, builder, BASE_URL, "tok-1")));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/dni/12345678")))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"No se encontraron resultados.\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findDni("12345678")).isEmpty();
        server.verify();
    }

    @Test
    void aFailingProviderFallsBackToTheNextOne() {
        RestClient.Builder paidBuilder = RestClient.builder();
        MockRestServiceServer paid = MockRestServiceServer.bindTo(paidBuilder).build();
        RestClient.Builder freeBuilder = RestClient.builder();
        MockRestServiceServer free = MockRestServiceServer.bindTo(freeBuilder).build();
        IdentityLookupClient client = new IdentityLookupClient(List.of(
                IdentityLookupClient.provider(IdentityLookupClient.Kind.APISPERU, paidBuilder, BASE_URL, "tok-1"),
                IdentityLookupClient.provider(IdentityLookupClient.Kind.FREE, freeBuilder, BASE_URL, "")));
        paid.expect(requestTo(startsWith(BASE_URL + "/api/v1/dni/46779354"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        free.expect(requestTo(startsWith(BASE_URL + "/v1/dni")))
                .andRespond(withSuccess("""
                        {"nombres":"CARLOS ESTEBAN","apellidoPaterno":"FERIA","apellidoMaterno":"VILA"}""", MediaType.APPLICATION_JSON));

        DniInfo info = client.findDni("46779354").orElseThrow();

        assertThat(info.lastNames()).isEqualTo("FERIA VILA");
        paid.verify();
        free.verify();
    }
}
