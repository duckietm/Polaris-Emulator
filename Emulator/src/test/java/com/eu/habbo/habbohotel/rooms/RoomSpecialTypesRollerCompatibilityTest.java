package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSpecialTypesRollerCompatibilityTest {

    @Test
    void publicRollerMapRemainsLiveStableAndMutable() {
        RoomSpecialTypes specialTypes = new RoomSpecialTypes();
        Map<Integer, InteractionRoller> rollers = specialTypes.getRollers();

        rollers.put(17, null);

        assertSame(rollers, specialTypes.getRollers());
        assertTrue(specialTypes.getRollers().containsKey(17));
        rollers.remove(17);
    }
}
