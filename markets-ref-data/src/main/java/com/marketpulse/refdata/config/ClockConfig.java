package com.marketpulse.refdata.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock, so date-sensitive logic can be tested at a fixed "today" instead of
 * whatever day the suite happens to run on.
 *
 * <p>{@code systemDefaultZone()} preserves the existing behaviour exactly: the containers set
 * {@code TZ=Asia/Kolkata}, which is the zone NSE trading days are expressed in. If this service is
 * ever run somewhere that does not set {@code TZ}, pin this to the same zone as
 * {@code nse.scheduler.zone} rather than relying on the host default.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
