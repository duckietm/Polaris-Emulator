package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.messages.ClientMessage;

/** The official AIR save layout: ints, string, picks, the kind's slot, sources, variables, second picks. */
class InteractionWiredReadSettingsTest {

    private static ByteBuf save(Integer slot) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(2).writeInt(5).writeInt(6); // int params
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(text.length).writeBytes(text);
        buffer.writeInt(1).writeInt(77); // furni picks
        if (slot != null) buffer.writeInt(slot);
        buffer.writeInt(1).writeInt(100); // furni sources
        buffer.writeInt(2).writeInt(0).writeInt(200); // user sources
        byte[] variable = "-110".getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(1).writeShort(variable.length).writeBytes(variable); // variable ids
        buffer.writeInt(1).writeInt(88); // second furni picks
        return buffer;
    }

    @Test
    void anActionSaveReadsTheDelayAndEveryOfficialList() {
        WiredSettings settings = InteractionWired.readSettings(
                new ClientMessage(0, save(12)),
                InteractionWired.SaveKind.ACTION,
                WiredInputGuard.MAX_STRING_PARAM_LENGTH);

        assertArrayEquals(new int[] {5, 6}, settings.getIntParams());
        assertEquals("hello", settings.getStringParam());
        assertArrayEquals(new int[] {77}, settings.getFurniIds());
        assertEquals(12, settings.getDelay());
        assertArrayEquals(new int[] {100}, settings.getFurniSourceTypes());
        assertArrayEquals(new int[] {0, 200}, settings.getUserSourceTypes());
        assertArrayEquals(new String[] {"-110"}, settings.getVariableIds());
        assertArrayEquals(new int[] {88}, settings.getFurniIds2());
    }

    @Test
    void aConditionSaveReadsTheQuantifierAndATriggerSaveNoSlot() {
        WiredSettings condition = InteractionWired.readSettings(
                new ClientMessage(0, save(1)),
                InteractionWired.SaveKind.CONDITION,
                WiredInputGuard.MAX_STRING_PARAM_LENGTH);
        WiredSettings trigger = InteractionWired.readSettings(
                new ClientMessage(0, save(null)),
                InteractionWired.SaveKind.TRIGGER,
                WiredInputGuard.MAX_STRING_PARAM_LENGTH);

        assertEquals(1, condition.getQuantifierCode());
        assertArrayEquals(new int[] {88}, condition.getFurniIds2());
        assertArrayEquals(new int[] {88}, trigger.getFurniIds2());
    }

    @Test
    void theOfficialDelayGoesUpTo120Pulses() {
        assertEquals(120, WiredInputGuard.normalizeDelay(120));
        assertEquals(120, WiredInputGuard.normalizeDelay(500));
        assertEquals(0, WiredInputGuard.normalizeDelay(-5));
    }

    @Test
    void oversizedListsAreRejected() {
        ByteBuf buffer = Unpooled.buffer().writeInt(WiredInputGuard.MAX_SOURCE_TYPES + 1);
        assertThrows(
                IllegalArgumentException.class, () -> WiredInputGuard.readSourceTypes(new ClientMessage(0, buffer)));
    }
}
