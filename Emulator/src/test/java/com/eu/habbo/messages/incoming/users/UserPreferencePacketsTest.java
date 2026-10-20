package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UserPreferencePacketsTest {

    @Test
    void parsesTheOfficialChatPreferencesLayout() {
        UserPreferencePackets.ChatPreferences preferences = UserPreferencePackets.parseChatPreferences(
                message(buffer().bool(false).integer(1).integer(2).integer(0)));

        assertEquals(1, preferences.chatMode());
        assertEquals(2, preferences.chatBubbleWidth());
        assertEquals(0, preferences.chatScrollSpeed());
    }

    @Test
    void fallsBackToFreeFlowAndNormalForUnknownChatValues() {
        UserPreferencePackets.ChatPreferences preferences = UserPreferencePackets.parseChatPreferences(
                message(buffer().bool(false).integer(7).integer(-1).integer(9)));

        assertEquals(0, preferences.chatMode());
        assertEquals(1, preferences.chatBubbleWidth());
        assertEquals(1, preferences.chatScrollSpeed());
    }

    @Test
    void parsesTheOnlineIndicatorPreferenceAndFallsBackToEveryone() {
        assertEquals(2, UserPreferencePackets.parseOnlineIndicatorPreference(message(buffer().integer(2))));
        assertEquals(0, UserPreferencePackets.parseOnlineIndicatorPreference(message(buffer().integer(5))));
        assertEquals(0, UserPreferencePackets.parseOnlineIndicatorPreference(message(buffer().integer(-1))));
    }

    @Test
    void readsTheWiredWhisperSwitchFromTheOfficialWiredMenuLayout() {
        assertTrue(UserPreferencePackets.parseWiredWhisperDisabled(message(buffer().bool(true)
                .bool(true)
                .bool(false)
                .integer(0)
                .bool(true)
                .bool(false)
                .string("default"))));
        assertFalse(UserPreferencePackets.parseWiredWhisperDisabled(message(buffer().bool(false)
                .bool(false)
                .bool(false)
                .integer(0)
                .bool(false)
                .bool(true)
                .string(""))));
    }

    @Test
    void readsAndEncodesTheWholeWiredMenuPreferences() {
        UserPreferencePackets.WiredMenuPreferences preferences =
                UserPreferencePackets.parseWiredMenuPreferences(message(buffer().bool(true)
                        .bool(false)
                        .bool(true)
                        .integer(0)
                        .bool(true)
                        .bool(true)
                        .string("volter<script>")));

        assertTrue(preferences.wiredWhisperDisabled());
        assertEquals("1,0,1,1,volterscript", preferences.encode());
    }

    @Test
    void readsThePlaytestSwitchBackFromTheStoredPreferences() {
        assertTrue(UserPreferencePackets.isPlayTestModeStored("1,0,1,1,volter"));
        assertFalse(UserPreferencePackets.isPlayTestModeStored("1,1,0,1,default"));
        assertFalse(UserPreferencePackets.isPlayTestModeStored(""));
        assertFalse(UserPreferencePackets.isPlayTestModeStored(null));
        assertFalse(UserPreferencePackets.isPlayTestModeStored("1,1"));

        UserPreferencePackets.WiredMenuPreferences preferences =
                new UserPreferencePackets.WiredMenuPreferences(false, false, true, false, false, "");
        assertTrue(UserPreferencePackets.isPlayTestModeStored(preferences.encode()));
    }

    private static ClientMessage message(BufferBuilder builder) {
        return new ClientMessage(0, builder.value);
    }

    private static BufferBuilder buffer() {
        return new BufferBuilder();
    }

    private static final class BufferBuilder {
        private final ByteBuf value = Unpooled.buffer();

        private BufferBuilder integer(int value) {
            this.value.writeInt(value);
            return this;
        }

        private BufferBuilder bool(boolean value) {
            this.value.writeByte(value ? 1 : 0);
            return this;
        }

        private BufferBuilder string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            this.value.writeShort(bytes.length);
            this.value.writeBytes(bytes);
            return this;
        }
    }
}
