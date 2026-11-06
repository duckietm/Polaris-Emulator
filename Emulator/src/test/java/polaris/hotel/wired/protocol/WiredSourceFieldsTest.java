package polaris.hotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.effects.WiredEffectTeleport;
import polaris.hotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRandom;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

/** The official definition carries a box's furni and user source in their own fields. */
class WiredSourceFieldsTest {

    @Test
    void aBoxSendsItsSourcesInTheOfficialFields() throws Exception {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));
            ConfigurationManager config = mock(ConfigurationManager.class);
            when(config.getInt(anyString())).thenReturn(20);
            when(config.getInt(anyString(), anyInt())).thenReturn(120);
            emulator.when(Emulator::getConfig).thenReturn(config);

            WiredEffectToggleFurni toggle = new WiredEffectToggleFurni(1, 1, mock(Item.class), "", 0, 0);
            WiredSettings settings = new WiredSettings(new int[] {0}, "", new int[0], -1);
            settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
            toggle.saveData(settings, null);

            assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SELECTOR}, sources(toggle)[0]);
            assertArrayEquals(new int[0], sources(toggle)[1]);

            WiredEffectTeleport teleport = new WiredEffectTeleport(2, 1, mock(Item.class), "", 0, 0);
            int[][] both = sources(teleport);
            assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, both[0]);
            assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, both[1]);

            WiredExtraRandom random = new WiredExtraRandom(3, 1, mock(Item.class), "", 0, 0);
            assertArrayEquals(new int[0], sources(random)[0]);
            assertArrayEquals(new int[0], sources(random)[1]);
        }
    }

    /** The furni and user sources the box's official definition carries. */
    private static int[][] sources(polaris.hotel.items.interactions.InteractionWired box) {
        WiredDefinitionWire.Holder holder = WiredHolders.of(box);
        ServerMessage legacy = new ServerMessage(1);
        box.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
        ServerMessage official = new ServerMessage(2);
        WiredDefinitionWire.writeOfficial(legacy, holder, official, box);

        ByteBuf in = official.get();
        in.skipBytes(6);
        in.readInt();
        in.skipBytes(in.readInt() * 4);
        in.skipBytes(in.readInt() * 4);
        in.readInt();
        in.readInt();
        in.skipBytes(in.readUnsignedShort());
        in.skipBytes(in.readInt() * 4);
        int variables = in.readInt();
        for (int i = 0; i < variables; i++) in.skipBytes(in.readUnsignedShort());
        int[] furni = new int[in.readInt()];
        for (int i = 0; i < furni.length; i++) furni[i] = in.readInt();
        int[] users = new int[in.readInt()];
        for (int i = 0; i < users.length; i++) users[i] = in.readInt();
        in.release();
        return new int[][] {furni, users};
    }
}
