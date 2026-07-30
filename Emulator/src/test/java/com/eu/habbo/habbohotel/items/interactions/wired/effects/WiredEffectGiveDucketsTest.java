package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import org.junit.jupiter.api.Test;

class WiredEffectGiveDucketsTest {

    @Test
    void usesThePixelGrantPath() {
        Room room = mock(Room.class);
        RoomUnit actor = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        when(room.getHabbo(actor)).thenReturn(habbo);

        WiredEffectGiveDuckets effect = new WiredEffectGiveDuckets(42, 7, mock(Item.class), "0", 0, 0);
        WiredSettings settings = new WiredSettings(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, "25", new int[0], 0);
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));

        assertTrue(effect.saveData(settings, null));
        effect.execute(context);

        verify(habbo).givePixels(25);
        verify(habbo, never()).givePoints(0, 25);
    }
}
