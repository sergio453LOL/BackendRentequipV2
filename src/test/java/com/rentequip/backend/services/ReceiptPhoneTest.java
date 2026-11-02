package com.rentequip.backend.services;

import com.rentequip.backend.exceptions.InvalidOperationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Phones are stored as the user typed them; WhatsApp (Twilio) only accepts E.164. */
class ReceiptPhoneTest {

    @Test
    void addsPeruCodeToANineDigitMobile() {
        assertThat(ReceiptService.toE164("987654321")).isEqualTo("+51987654321");
        assertThat(ReceiptService.toE164("987 654 321")).isEqualTo("+51987654321");
    }

    @Test
    void keepsNumbersThatAlreadyHaveACountryCode() {
        assertThat(ReceiptService.toE164("+51 987-654-321")).isEqualTo("+51987654321");
        assertThat(ReceiptService.toE164("51987654321")).isEqualTo("+51987654321");
    }

    @Test
    void rejectsMissingOrLandlineNumbers() {
        assertThrows(InvalidOperationException.class, () -> ReceiptService.toE164(null));
        assertThrows(InvalidOperationException.class, () -> ReceiptService.toE164("  "));
        assertThrows(InvalidOperationException.class, () -> ReceiptService.toE164("014567890"));
    }
}
