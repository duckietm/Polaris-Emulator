package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.messages.ClientMessage;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class WiredConditionLegacySaveAbiTest {

    @Test
    void historicalPacketSaveDescriptorRemainsLinkableForPluginConditions() throws Exception {
        Method method = InteractionWiredCondition.class.getMethod("saveData", ClientMessage.class);

        assertEquals(boolean.class, method.getReturnType());
    }
}
