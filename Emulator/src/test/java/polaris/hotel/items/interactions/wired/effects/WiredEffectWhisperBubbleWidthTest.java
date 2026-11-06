package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.hotel.users.User;
import polaris.messages.outgoing.wired.WiredEffectDataComposer;

/**
 * The "show message" box takes a third int (official [visibility, style, width]), the bubble width the message should use: -1 keeps the
 * room setting, 0/1/2 are wide/normal/thin as the room setting names them. A box saved before the
 * slot existed keeps the room setting.
 */
class WiredEffectWhisperBubbleWidthTest {

    // saveData clamps the message against the hotel configuration, which nothing installs in a unit
    // test; a minimal one with the two limits is enough.
    @BeforeAll
    static void installConfiguration() throws Exception {
        Path config = Files.createTempFile("polaris-whisper-test", ".ini");
        Files.writeString(config, "hotel.wired.show_message.max_length=200\nhotel.wired.show_message.max_lines=8\n");
        setEmulatorConfig(new ConfigurationManager(config.toString()));
    }

    @AfterAll
    static void removeConfiguration() throws Exception {
        setEmulatorConfig(null);
    }

    private static void setEmulatorConfig(ConfigurationManager manager) throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, manager);
    }

    @Test
    void aBoxSavedWithTwoIntsKeepsTheRoomSetting() throws Exception {
        WiredEffectWhisper effect = saved(new int[] {0, 34});

        assertEquals(-1, effect.getBubbleWidthOverride());
    }

    @Test
    void theThirdIntIsTheBubbleWidth() throws Exception {
        assertEquals(2, saved(new int[] {0, 34, 2}).getBubbleWidthOverride());
        assertEquals(0, saved(new int[] {0, 34, 0}).getBubbleWidthOverride());
    }

    @Test
    void aWidthThatNamesNothingKeepsTheRoomSetting() throws Exception {
        assertEquals(-1, saved(new int[] {0, 34, 9}).getBubbleWidthOverride());
        assertEquals(-1, saved(new int[] {0, 34, -3}).getBubbleWidthOverride());
    }

    @Test
    void theDialogGetsTheOfficialThreeIntsBackWithTheWidthLast() throws Exception {
        WiredEffectWhisper effect = saved(new int[] {1, 34, 1});
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getTriggers(anyInt(), anyInt())).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        ByteBuf packet = new WiredEffectDataComposer(effect, room).compose().get();
        try {
            packet.skipBytes(6); // frame length and header
            // official layout: furniLimit, stuffIds, stuffIds2, spriteId, id
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.skipBytes(packet.readShort()); // the message
            // official [visibility, style, width]; the user source is the official slot
            assertEquals(3, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(34, packet.readInt());
            assertEquals(1, packet.readInt());
        } finally {
            packet.release();
        }
    }

    private static WiredEffectWhisper saved(int[] intParams) throws Exception {
        Item boxBase = mock(Item.class);
        when(boxBase.getType()).thenReturn(FurnitureType.FLOOR);
        when(boxBase.getSpriteId()).thenReturn(1234);
        WiredEffectWhisper effect = new WiredEffectWhisper(77, 1, boxBase, "", 0, 0);

        User user = mock(User.class);
        when(user.hasPermission(anyString())).thenReturn(true);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

        effect.saveData(new WiredSettings(intParams, "hello", new int[0], 0), client);
        return effect;
    }
}
