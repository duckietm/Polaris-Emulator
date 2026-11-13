package polaris.networking.gameserver.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingRoundTripTest {
    @Test
    void measuresFromThePingToItsPong() {
        PingRoundTrip roundTrip = new PingRoundTrip();

        roundTrip.sent(1_000L);

        assertEquals(250L, roundTrip.answered(1_250L));
    }

    @Test
    void aPongThatAnswersNoPingIsNotASample() {
        PingRoundTrip roundTrip = new PingRoundTrip();

        assertEquals(PingRoundTrip.NOT_WAITING, roundTrip.answered(500L));

        roundTrip.sent(1_000L);
        roundTrip.answered(1_100L);

        assertEquals(PingRoundTrip.NOT_WAITING, roundTrip.answered(1_200L));
    }

    @Test
    void anUnansweredPingKeepsItsStartWhenTheNextOneGoesOut() {
        PingRoundTrip roundTrip = new PingRoundTrip();

        roundTrip.sent(1_000L);
        roundTrip.sent(31_000L);

        assertEquals(31_000L, roundTrip.answered(32_000L));
    }

    @Test
    void theNextPingStartsAFreshMeasurement() {
        PingRoundTrip roundTrip = new PingRoundTrip();

        roundTrip.sent(1_000L);
        roundTrip.answered(1_100L);
        roundTrip.sent(31_000L);

        assertEquals(40L, roundTrip.answered(31_040L));
    }
}
