package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredDefinitionWire;
import polaris.messages.ServerMessage;

/** The official neighborhood wire form: spiral bitmask drawing, root tile, merged source slot. */
class WiredNeighborhoodWireTest {

    @Test
    void theSpiralStartsAtTheCentreAndTurnsLikeTheOfficialClient() {
        // Ranks 0..4: (0,0), (1,0), (1,-1), (0,-1), (-1,-1).
        assertArrayEquals(
                new int[] {0b11111, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                WiredNeighborhoodWire.encode(List.of(
                        new int[] {0, 0}, new int[] {1, 0}, new int[] {1, -1}, new int[] {0, -1}, new int[] {-1, -1})));
        assertEquals(14, WiredNeighborhoodWire.MASK_INTS);
    }

    @Test
    void aDrawingRoundTripsAndDropsTilesOffTheFloor() {
        List<int[]> tiles = List.of(new int[] {10, 10}, new int[] {-10, 3}, new int[] {2, -7}, new int[] {11, 0});
        int[] mask = WiredNeighborhoodWire.encode(tiles);
        int[] params = new int[3 + mask.length];
        System.arraycopy(mask, 0, params, 3, mask.length);

        List<int[]> decoded = WiredNeighborhoodWire.decode(params, 3);
        assertEquals(3, decoded.size());
        assertTrue(decoded.stream().anyMatch(t -> t[0] == 10 && t[1] == 10));
        assertTrue(decoded.stream().anyMatch(t -> t[0] == -10 && t[1] == 3));
        assertTrue(decoded.stream().anyMatch(t -> t[0] == 2 && t[1] == -7));
    }

    @Test
    void aBoxSavesAndSendsTheOfficialLayout() throws Exception {
        WiredEffectUsersNeighborhood box = new WiredEffectUsersNeighborhood(1, 1, mock(Item.class), "", 0, 0);
        int[] drawing = WiredNeighborhoodWire.encode(List.of(new int[] {1, 0}, new int[] {0, 1}));
        int[] params = new int[3 + drawing.length];
        params[0] = 1;
        params[1] = 2;
        params[2] = -3;
        System.arraycopy(drawing, 0, params, 3, drawing.length);
        WiredSettings settings = new WiredSettings(params, "", new int[0], -1);
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_SIGNAL});
        settings.setSelectorInvert(true);

        assertTrue(box.saveData(settings, null));
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SIGNAL}, box.officialUserSources());
        assertTrue(box.selectorInvert());

        ServerMessage legacy = new ServerMessage(1);
        box.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
        ServerMessage official = new ServerMessage(2);
        WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.SELECTOR, official, box);

        ByteBuf in = official.get();
        in.skipBytes(6);
        in.readInt();
        in.skipBytes(in.readInt() * 4);
        in.skipBytes(in.readInt() * 4);
        in.readInt();
        in.readInt();
        in.skipBytes(in.readUnsignedShort());
        int[] sent = new int[in.readInt()];
        for (int i = 0; i < sent.length; i++) sent[i] = in.readInt();
        in.readInt(); // variable ids
        int[] furniSources = new int[in.readInt()];
        for (int i = 0; i < furniSources.length; i++) furniSources[i] = in.readInt();
        int[] userSources = new int[in.readInt()];
        for (int i = 0; i < userSources.length; i++) userSources[i] = in.readInt();
        assertEquals(12, in.readInt(), "official selector code");
        boolean filter = in.readBoolean();
        boolean invert = in.readBoolean();
        in.release();

        assertArrayEquals(params, sent);
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SIGNAL}, userSources);
        assertEquals(false, filter);
        assertEquals(true, invert);
    }
}
