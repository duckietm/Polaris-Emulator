package com.eu.habbo.habbohotel.pets.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MonsterplantBreedingRequestsTest {
    @Test
    void theRequestIsFiledUnderThePlantThatWasAskedFor() {
        MonsterplantBreedingRequests requests = MonsterplantBreedingRequests.getInstance();
        requests.close(401);

        requests.open(7, 400, 401);

        assertNull(requests.on(400), "the plant that asked holds no request");

        MonsterplantBreedingRequests.Request request = requests.on(401);

        assertNotNull(request);
        assertEquals(7, request.requesterId());
        assertEquals(400, request.requesterPetId());

        requests.close(401);
    }

    @Test
    void askingAgainReplacesWhatStoodBefore() {
        MonsterplantBreedingRequests requests = MonsterplantBreedingRequests.getInstance();

        requests.open(7, 400, 402);
        requests.open(9, 500, 402);

        assertEquals(9, requests.on(402).requesterId());
        assertEquals(500, requests.on(402).requesterPetId());

        requests.close(402);
    }

    @Test
    void aClosedRequestIsGoneAndClosingAgainSaysSo() {
        MonsterplantBreedingRequests requests = MonsterplantBreedingRequests.getInstance();

        requests.open(7, 400, 403);

        assertNotNull(requests.close(403));
        assertNull(requests.on(403));
        assertNull(requests.close(403));
    }
}
