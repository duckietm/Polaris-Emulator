package polaris.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.messages.ClientMessage;

class WiredConditionLegacySaveAbiTest {

    @Test
    void historicalPacketSaveDescriptorRemainsLinkableForPluginConditions() throws Exception {
        Method method = InteractionWiredCondition.class.getMethod("saveData", ClientMessage.class);

        assertEquals(boolean.class, method.getReturnType());
    }
}
