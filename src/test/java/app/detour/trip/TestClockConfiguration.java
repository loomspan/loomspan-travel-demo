package app.detour.trip;

import app.detour.common.ClockConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfiguration {

    @Bean
    @Primary
    public TestClock testClock() {
        return new TestClock(Clock.system(ClockConfiguration.PDX_ZONE));
    }

    public static class TestClock extends Clock {
        private volatile Clock delegate;

        public TestClock(Clock delegate) {
            this.delegate = delegate;
        }

        public void setInstant(Instant instant) {
            this.delegate = Clock.fixed(instant, ClockConfiguration.PDX_ZONE);
        }

        public void setInstant(Instant instant, ZoneId zoneId) {
            this.delegate = Clock.fixed(instant, zoneId);
        }

        public void reset() {
            this.delegate = Clock.system(ClockConfiguration.PDX_ZONE);
        }

        @Override public ZoneId getZone() { return delegate.getZone(); }
        @Override public Clock withZone(ZoneId zone) { return delegate.withZone(zone); }
        @Override public Instant instant() { return delegate.instant(); }
    }
}
