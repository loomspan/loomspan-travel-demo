package app.detour.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {
    public static final ZoneId PDX_ZONE = ZoneId.of("America/Los_Angeles");

    @Bean
    @ConditionalOnMissingBean
    public Clock clock(@Value("${detour.clock.fixed-instant:}") String fixedInstant) {
        if (fixedInstant != null && !fixedInstant.isBlank()) {
            return Clock.fixed(Instant.parse(fixedInstant), PDX_ZONE);
        }
        return Clock.system(PDX_ZONE);
    }
}
