package com.rentequip.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/** On a UTC host (EC2), "today" must still be Lima's: main() fixes the JVM's default zone before Spring starts. */
class MarketTimeZoneTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void aServerInUtcStillWorksInLimaTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        BackendApplication.useMarketTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Lima");
    }
}
