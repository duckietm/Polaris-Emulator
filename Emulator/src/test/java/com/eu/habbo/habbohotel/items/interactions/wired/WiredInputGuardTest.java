package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WiredInputGuardTest {

    @Test
    void rejectsOversizedStringParams() {
        String input = "x".repeat(WiredInputGuard.MAX_STRING_PARAM_LENGTH + 1);
        ClientMessage message = new ClientMessage(1, stringBuffer(input));

        assertThrows(IllegalArgumentException.class, () -> WiredInputGuard.readStringParam(message));
    }

    @Test
    void permitsOnlyExplicitLargePayloadsUpToTheHardLimit() {
        String input = "x".repeat(WiredLargePayload.MAX_STRING_PARAM_LENGTH + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> WiredInputGuard.readStringParam(new ClientMessage(1, stringBuffer(input)), 2048));
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredInputGuard.readStringParam(new ClientMessage(1, stringBuffer(input)), Integer.MAX_VALUE));
    }

    @Test
    void filtersNonPositiveFurniIds() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(0);
        buffer.writeInt(-1);
        buffer.writeInt(2);

        assertArrayEquals(new int[] {1, 2}, WiredInputGuard.readFurniIds(new ClientMessage(1, buffer)));
    }

    @Test
    void clampsDelayAndSelectionCode() {
        assertEquals(0, WiredInputGuard.normalizeDelay(-10));
        assertEquals(
                WiredInputGuard.DEFAULT_MAX_DELAY,
                WiredInputGuard.normalizeDelay(WiredInputGuard.DEFAULT_MAX_DELAY + 1));
        assertEquals(-1, WiredInputGuard.normalizeStuffSelectionCode(99));
        assertEquals(2, WiredInputGuard.normalizeStuffSelectionCode(2));
    }

    private static ByteBuf stringBuffer(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
        return buffer;
    }
}
