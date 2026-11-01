package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A save of custom values keeps only the furni's own keys, each as the furni accepts it. Any key used to
 * be added (20 per packet, with no total), and any text was stored for a number.
 */
class AdvertisingSaveSanitizeTest {

    @Test
    void unknownKeysAndRefusedValuesAreDropped() {
        InteractionCustomValues furni = mock(InteractionCustomValues.class);
        when(furni.acceptsKey(anyString())).thenReturn(false);
        when(furni.acceptsKey("tilesLeft")).thenReturn(true);
        when(furni.acceptsKey("tilesRight")).thenReturn(true);
        when(furni.sanitizeValue("tilesLeft", "4")).thenReturn("4");
        when(furni.sanitizeValue("tilesRight", "abc")).thenReturn(null);

        Map<String, String> received = new LinkedHashMap<>();
        received.put("tilesLeft", "4");
        received.put("tilesRight", "abc");
        received.put("junk", "x".repeat(500));

        assertEquals(Map.of("tilesLeft", "4"), AdvertisingSaveEvent.sanitize(furni, received));
    }
}
