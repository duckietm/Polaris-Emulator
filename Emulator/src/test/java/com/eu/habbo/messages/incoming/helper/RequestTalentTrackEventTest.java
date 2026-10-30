package com.eu.habbo.messages.incoming.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import org.junit.jupiter.api.Test;

class RequestTalentTrackEventTest {

    @Test
    void aKnownTrackIsReadWhateverItsCase() {
        assertEquals(TalentTrackType.CITIZENSHIP, RequestTalentTrackEvent.parseType("citizenship"));
        assertEquals(TalentTrackType.HELPER, RequestTalentTrackEvent.parseType(" Helper "));
    }

    @Test
    void anUnknownTrackIsIgnoredInsteadOfThrowing() {
        assertNull(RequestTalentTrackEvent.parseType("nonsense"));
        assertNull(RequestTalentTrackEvent.parseType(""));
        assertNull(RequestTalentTrackEvent.parseType(null));
    }

    @Test
    void requestsAreRateLimited() {
        assertTrue(new RequestTalentTrackEvent().getRatelimit() > 0);
    }
}
