package polaris.hotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.effects.WiredEffectWhisper;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserSaysKeyword;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.ServerMessage;

/** The text-placeholder context (wired context type 7) of the message boxes. */
class WiredTextPlaceholderContextTest {

    @Test
    void listsTheStacksPlaceholdersTriggerTextOnAKeywordStackAndTheCapturers() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredExtraTextOutputUsername username = mock(WiredExtraTextOutputUsername.class);
        when(username.getPlaceholderName()).thenReturn("name");
        WiredExtraTextInputVariable input = mock(WiredExtraTextInputVariable.class);
        when(input.getCapturerName()).thenReturn("price");
        when(types.getExtras(anyInt(), anyInt()))
                .thenReturn(new LinkedHashSet<InteractionWiredExtra>(List.of(username, input)));
        when(types.getTriggers(anyInt(), anyInt()))
                .thenReturn(
                        new LinkedHashSet<InteractionWiredTrigger>(List.of(mock(WiredTriggerUserSaysKeyword.class))));

        WiredTextPlaceholderContext.Names names = WiredTextPlaceholderContext.namesFor(room, 1, 1);

        assertEquals(List.of("name", "trigger_text"), names.placeholders());
        assertEquals(List.of("price"), names.capturers());

        ServerMessage out = new ServerMessage(1);
        WiredTextPlaceholderContext.write(out, names);
        ByteBuf in = out.get();
        in.skipBytes(6);
        assertEquals(1, in.readInt());
        assertEquals(7, in.readInt());
        assertEquals(2, in.readInt());
        assertEquals("name", readString(in));
        assertEquals("trigger_text", readString(in));
        assertEquals(1, in.readInt());
        assertEquals("price", readString(in));
        assertEquals(0, in.readableBytes());
    }

    @Test
    void aRoomUsingTriggerTextIsFound() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredEffectWhisper whisper = mock(WiredEffectWhisper.class);
        when(types.getEffects()).thenReturn(Set.<InteractionWiredEffect>of(whisper));

        when(whisper.getWiredData()).thenReturn("{\"message\":\"You said $(trigger_text)\"}");
        assertTrue(WiredTextPlaceholderContext.usesTriggerText(room));

        when(whisper.getWiredData()).thenReturn("{\"message\":\"hello\"}");
        assertFalse(WiredTextPlaceholderContext.usesTriggerText(room));
    }

    private static String readString(ByteBuf in) {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
