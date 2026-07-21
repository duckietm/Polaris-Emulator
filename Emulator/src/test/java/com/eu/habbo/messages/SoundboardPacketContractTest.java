package com.eu.habbo.messages;

import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardSettingsComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundboardPacketContractTest {

    @Test
    void settingsCarryPersonalizedCooldownBeforeTheFilteredSounds() {
        SoundboardSound bell =
                new SoundboardSound(7, "Campanella", "/sounds/soundboard/campanella.mp3", 1);
        ByteBuf packet = new SoundboardSettingsComposer(true, 60, List.of(bell)).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(60, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals("Campanella", readString(packet));
        assertEquals("/sounds/soundboard/campanella.mp3", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void playCarriesAuthoritativeSoundAndActorMetadata() {
        ByteBuf packet = new SoundboardPlayComposer(
                        7,
                        "/sounds/soundboard/campanella.mp3",
                        "Campanella",
                        42,
                        3,
                        "Simoleo")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(7, packet.readInt());
        assertEquals("/sounds/soundboard/campanella.mp3", readString(packet));
        assertEquals("Campanella", readString(packet));
        assertEquals(42, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("Simoleo", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8).toString();
    }
}
