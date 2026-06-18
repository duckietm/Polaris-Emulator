package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredInputGuardTest {

    @Test
    void trimsOversizedStringParams() {
        String input = "x".repeat(WiredInputGuard.MAX_STRING_PARAM_LENGTH + 1);
        ClientMessage message = new ClientMessage(1, stringBuffer(input));

        assertEquals(WiredInputGuard.MAX_STRING_PARAM_LENGTH,
                WiredInputGuard.readStringParam(message).length());
    }

    @Test
    void filtersNonPositiveFurniIds() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(0);
        buffer.writeInt(-1);
        buffer.writeInt(2);

        assertArrayEquals(new int[]{1, 2}, WiredInputGuard.readFurniIds(new ClientMessage(1, buffer)));
    }

    @Test
    void clampsDelayAndSelectionCode() {
        assertEquals(0, WiredInputGuard.normalizeDelay(-10));
        assertEquals(WiredInputGuard.DEFAULT_MAX_DELAY, WiredInputGuard.normalizeDelay(WiredInputGuard.DEFAULT_MAX_DELAY + 1));
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
