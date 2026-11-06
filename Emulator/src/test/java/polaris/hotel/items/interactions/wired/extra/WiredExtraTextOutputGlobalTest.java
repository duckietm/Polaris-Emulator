package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

class WiredExtraTextOutputGlobalTest {

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(5200);
        return base;
    }

    private static WiredExtraTextOutputGlobal box(int id) {
        return new WiredExtraTextOutputGlobal(id, 1, base(), "", 0, 0);
    }

    private static Room room(int id, int ownerId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getOwnerId()).thenReturn(ownerId);
        return room;
    }

    private static WiredSettings settings(int[] params, String text, Room room) {
        WiredSettings settings = new WiredSettings(params, text, new int[0], 0);
        settings.setRoom(room);
        return settings;
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private record Body(String text, int[] params, int code) {}

    private static Body body(WiredExtraTextOutputGlobal box) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, null);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readByte();
            buffer.readInt();
            buffer.readInt();
            buffer.readInt();
            buffer.readInt();
            byte[] text = new byte[buffer.readShort()];
            buffer.readBytes(text);
            int[] params = new int[buffer.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = buffer.readInt();
            buffer.readInt();
            return new Body(new String(text, StandardCharsets.UTF_8), params, buffer.readInt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void aTypedValueIsSavedCleanedAndSentBackToTheEditor() throws Exception {
        WiredExtraTextOutputGlobal box = box(40);

        assertTrue(box.saveData(settings(new int[] {0, 0, 99}, " $(greeting) \tHello\r\nthere", room(7, 3)), null));

        assertEquals("greeting", box.getPlaceholderName());
        assertEquals("$(greeting)", box.getPlaceholderToken());
        assertEquals("Hellothere", box.resolveText(null));

        Body body = body(box);
        assertEquals("greeting\tHellothere\t{\"rooms\":[]}", body.text());
        assertArrayEquals(new int[] {0, 0, 0}, body.params());
        assertEquals(WiredExtraTextOutputGlobal.CODE, body.code());
    }

    @Test
    void namesAndValuesAreCapped() throws Exception {
        WiredExtraTextOutputGlobal box = box(40);

        box.saveData(settings(new int[] {7}, "n".repeat(50) + "\t" + "v".repeat(300), room(7, 3)), null);

        assertEquals(
                WiredExtraTextOutputGlobal.MAX_PLACEHOLDER_NAME_LENGTH,
                box.getPlaceholderName().length());
        assertEquals(WiredExtraTextOutputGlobal.MAX_VALUE_LENGTH, box.getValue().length());
        assertEquals(WiredExtraTextOutputGlobal.MODE_FROM_VALUE, box.getMode());
    }

    @Test
    void anotherRoomWithoutASharedSourceIsRefused() {
        WiredExtraTextOutputGlobal box = box(40);

        assertThrows(
                WiredSaveException.class,
                () -> box.saveData(settings(new int[] {1, 0, 0}, "name\tsource", room(7, 3)), null));
        assertThrows(
                WiredSaveException.class,
                () -> box.saveData(settings(new int[] {1, 0, 7}, "name\tsource", room(7, 3)), null));
        assertThrows(
                WiredSaveException.class,
                () -> box.saveData(settings(new int[] {1, 0, 12}, "name\t", room(7, 3)), null));
    }

    @Test
    void aStoredSourceSurvivesReloadAndFallsBackToItsLastText() throws Exception {
        WiredExtraTextOutputGlobal box = box(40);
        box.loadWiredData(
                row("{\"placeholderName\":\"motd\",\"mode\":1,\"value\":\"\",\"sourceRoomId\":12,"
                        + "\"sourcePlaceholderName\":\"news\",\"sourceValue\":\"Party at 8\"}"),
                null);

        assertEquals(WiredExtraTextOutputGlobal.MODE_FROM_ANOTHER_ROOM, box.getMode());
        assertEquals("Party at 8", box.resolveText(room(7, 3)));
        Body body = body(box);
        assertTrue(body.text().startsWith("motd\tnews\t"));
        assertArrayEquals(new int[] {1, 0, 12}, body.params());

        WiredExtraTextOutputGlobal reloaded = box(41);
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertEquals("news", reloaded.getSourcePlaceholderName());
        assertEquals(12, reloaded.getSourceRoomId());
    }

    @Test
    void aSourceWithoutARoomDegradesToAnEmptyTypedValue() throws Exception {
        WiredExtraTextOutputGlobal box = box(40);
        box.loadWiredData(row("{\"placeholderName\":\"motd\",\"mode\":1,\"sourceRoomId\":0}"), null);

        assertEquals(WiredExtraTextOutputGlobal.MODE_FROM_VALUE, box.getMode());
        assertEquals("", box.resolveText(null));
    }

    @Test
    void onlyTypedNamedPlaceholdersAreShared() throws Exception {
        ResultSet set = rows(
                new Object[] {12, "Lobby", "{\"placeholderName\":\"news\",\"mode\":0,\"value\":\"Hi\"}"},
                new Object[] {12, "Lobby", "{\"placeholderName\":\"news\",\"mode\":0,\"value\":\"dupe\"}"},
                new Object[] {12, "Lobby", "{\"placeholderName\":\"linked\",\"mode\":1,\"sourceRoomId\":5}"},
                new Object[] {13, "Cafe\t", "{\"placeholderName\":\"\",\"mode\":0,\"value\":\"x\"}"},
                new Object[] {13, "Cafe\t", "not json"},
                new Object[] {13, "Cafe\t", "{\"placeholderName\":\"$(menu)\",\"mode\":0,\"value\":\"Tea\"}"});

        List<WiredGlobalPlaceholderSupport.SharedPlaceholder> shared = WiredGlobalPlaceholderSupport.readShared(set);

        assertEquals(
                List.of(
                        new WiredGlobalPlaceholderSupport.SharedPlaceholder(12, "Lobby", "news", "Hi"),
                        new WiredGlobalPlaceholderSupport.SharedPlaceholder(13, "Cafe", "menu", "Tea")),
                shared);
    }

    @Test
    void theSharedListIsCapped() throws Exception {
        Object[][] data = new Object[WiredGlobalPlaceholderSupport.MAX_SHARED_PLACEHOLDERS + 20][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Object[] {12, "Lobby", "{\"placeholderName\":\"p" + i + "\",\"mode\":0,\"value\":\"v\"}"};
        }

        assertEquals(
                WiredGlobalPlaceholderSupport.MAX_SHARED_PLACEHOLDERS,
                WiredGlobalPlaceholderSupport.readShared(rows(data)).size());
    }

    @Test
    void aLoadedSourceIsReadOnlyForTheSameOwner() throws Exception {
        WiredExtraTextOutputGlobal typed = box(50);
        typed.saveData(settings(new int[] {0}, "news\tFresh", room(12, 3)), null);
        WiredExtraTextOutputGlobal linked = box(51);
        linked.loadWiredData(
                row("{\"placeholderName\":\"news\",\"mode\":1,\"sourceRoomId\":9,\"sourcePlaceholderName\":\"x\"}"),
                null);
        Room source = room(12, 3);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(linked, typed));
        when(special.getExtras()).thenReturn(extras);
        when(source.getRoomSpecialTypes()).thenReturn(special);

        assertEquals("Fresh", WiredGlobalPlaceholderSupport.typedValueIn(source, 3, "news"));
        assertNull(WiredGlobalPlaceholderSupport.typedValueIn(source, 4, "news"));
        assertNull(WiredGlobalPlaceholderSupport.typedValueIn(source, 3, "missing"));
        assertNull(WiredGlobalPlaceholderSupport.typedValueIn(null, 3, "news"));
    }

    private static ResultSet rows(Object[]... data) throws SQLException {
        List<Object[]> list = new ArrayList<>(List.of(data));
        Iterator<Object[]> iterator = list.iterator();
        Object[][] current = new Object[1][];
        ResultSet set = mock(ResultSet.class);
        when(set.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) return false;
            current[0] = iterator.next();
            return true;
        });
        when(set.getInt("room_id")).thenAnswer(invocation -> current[0][0]);
        when(set.getString("room_name")).thenAnswer(invocation -> current[0][1]);
        when(set.getString("wired_data")).thenAnswer(invocation -> current[0][2]);
        return set;
    }
}
