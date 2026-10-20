package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The leaderboard sends and reads the variable list's ids in its official variable ids; a box stored with bare box
 * ids reads as the same variables and is written with the list ids on its next save.
 */
class WiredExtraVariableLeaderboardIdsTest {
    /** Sort highest, 1 column (username), filter on without compare, 2 display names, at (1, 1). */
    private static final int[] INTS = {0, 1, 0, -1, -1, -1, 1, 0, 0, 2, 1, 1, 0, 0, 3, 0, 0, 0, 0};

    private static final String TEXT =
            String.join("\t", "Top", "", "Player", "", "", "", "", "Gold", "Wins", "", "", "", "", "", "", "", "");

    private static String oldRow() {
        return WiredManager.getGson()
                .toJson(new WiredExtraVariableLeaderboard.JsonData(
                        INTS, new String[] {"77", "0", "1200", "900", "0", "0", "0", "0", "0", "0", "0", "0"}, TEXT));
    }

    private static ResultSet stored(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static WiredExtraVariableLeaderboard box() {
        return new WiredExtraVariableLeaderboard(50, 1, mock(Item.class), "", 0, 0);
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], 0);
        settings.setVariableIds(variableIds);
        return settings;
    }

    @Test
    void anOldRowLoadsWithTheListIdsAndIsWrittenWithThemOnTheNextSave() throws Exception {
        WiredExtraVariableLeaderboard box = box();
        box.loadWiredData(stored(oldRow()), null);

        String[] listIds = {"user:77", "0", "user:1200", "user:900", "0", "0", "0", "0", "0", "0", "0", "0"};
        assertTrue(box.isPublished());
        assertArrayEquals(listIds, box.officialVariableIds());
        assertEquals("user:77", box.getConfig().filterVariableId());
        assertEquals("Gold", box.getConfig().displayOverrides().get("user:1200"));
        assertTrue(box.getWiredData().contains("\"user:900\""), box.getWiredData());

        // A row already written with the list ids loads as the very same box.
        WiredExtraVariableLeaderboard resaved = box();
        resaved.loadWiredData(stored(box.getWiredData()), null);
        assertArrayEquals(listIds, resaved.officialVariableIds());
        assertEquals(box.getWiredData(), resaved.getWiredData());
    }

    @Test
    void theDefinitionSendsTheTextAloneAndTheListIdsAsItsVariableIds() throws Exception {
        WiredExtraVariableLeaderboard box = box();
        box.loadWiredData(stored(oldRow()), null);

        WiredEditorState state = WiredEditorState.of(box, mock(Room.class));
        assertEquals(TEXT, state.stringParam());
        assertArrayEquals(box.officialVariableIds(), state.variableIds());
    }

    @Test
    void theEditorContextOffersTheRankedVariablesUnderTheirListIds() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        WiredExtraUserVariable coins = mock(WiredExtraUserVariable.class);
        when(coins.getId()).thenReturn(900);
        when(coins.getVariableName()).thenReturn("coins");
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras(0, 0)).thenReturn(new LinkedHashSet<>(List.of(coins)));
        WiredExtraVariableLeaderboard box = box();
        when(specialTypes.getExtras()).thenReturn(new LinkedHashSet<>(List.of(coins, box)));

        ServerMessage message = new ServerMessage(Outgoing.WiredEffectDataComposer);
        box.serializeWiredData(message, room);
        ByteBuf body = message.get();
        try {
            body.skipBytes(6);
            body.skipBytes(1 + 4 * 4);
            readString(body);
            body.skipBytes(4 * (body.readInt() + 4));
            assertEquals(1, body.readInt());
            assertEquals(WiredExtraVariableLeaderboard.CONTEXT_TYPE, body.readInt());
            body.skipBytes(4 * 3);
            assertEquals(1, body.readInt());
            assertEquals(1, body.readInt());
            assertEquals("user:900", readString(body));
            assertEquals("coins", readString(body));
        } finally {
            body.release();
        }
    }

    @Test
    void aSaveReadsTheListIdsAndStillTheOlderForms() throws Exception {
        String[] listIds = {"user:77", "n", "user:1200", "user:900"};
        WiredExtraVariableLeaderboard official = box();
        assertTrue(official.saveData(settings(INTS, TEXT, listIds), null));

        // An older client: bare ids after the 17 text fields, no variable ids.
        WiredExtraVariableLeaderboard older = box();
        String wire = TEXT + "\t" + String.join("\t", "77", "0", "1200", "900", "0", "0", "0", "0", "0", "0", "0", "0");
        assertTrue(older.saveData(settings(INTS, wire), null));

        assertArrayEquals(official.officialVariableIds(), older.officialVariableIds());
        assertEquals("user:77", official.officialVariableIds()[0]);
        assertEquals("0", official.officialVariableIds()[1]);
        assertEquals(official.getWiredData(), older.getWiredData());
    }

    private static String readString(ByteBuf body) {
        byte[] text = new byte[body.readShort()];
        body.readBytes(text);
        return new String(text, StandardCharsets.UTF_8);
    }
}
