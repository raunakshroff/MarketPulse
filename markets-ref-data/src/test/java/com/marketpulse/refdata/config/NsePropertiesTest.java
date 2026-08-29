package com.marketpulse.refdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NsePropertiesTest {

    @Test
    void backfillDefaultsMatchTheValidatedNsePacing() {
        NseProperties properties = new NseProperties();

        assertThat(properties.getBackfill().getDelayMs()).isEqualTo(2000L);
        assertThat(properties.getBackfill().getMaxConsecutiveFailures()).isEqualTo(10);
        assertThat(properties.getBackfill().getEarliestDate()).isEqualTo(LocalDate.of(2019, 10, 1));
    }

    @Test
    void backfillSettingsAreOverridable() {
        NseProperties properties = new NseProperties();

        properties.getBackfill().setDelayMs(0L);
        properties.getBackfill().setMaxConsecutiveFailures(3);
        properties.getBackfill().setEarliestDate(LocalDate.of(2020, 1, 1));

        assertThat(properties.getBackfill().getDelayMs()).isZero();
        assertThat(properties.getBackfill().getMaxConsecutiveFailures()).isEqualTo(3);
        assertThat(properties.getBackfill().getEarliestDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
