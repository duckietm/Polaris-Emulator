package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WiredExtraVariableWebApiTest {
    private static final Pattern URL_SAFE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int OWNER = 7;
    private static final int ROOM = 90;

    @Test
    void mintedKeysAreUrlSafeAndCarryThirtyTwoRandomBytes() {
        String key = WiredExtraVariableWebApi.mintKey();

        assertEquals(43, key.length());
        assertTrue(URL_SAFE.matcher(key).matches(), key);
        assertTrue(WiredExtraVariableWebApi.isWellFormedKey(key));
    }

    @Test
    void everyMintedKeyIsDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(WiredExtraVariableWebApi.mintKey());
        }
        assertEquals(500, seen.size());
    }

    @Test
    void onlyWellFormedKeysHash() {
        String key = WiredExtraVariableWebApi.mintKey();

        assertEquals(32, WiredExtraVariableWebApi.hashKey(key).length);
        assertArrayEquals(WiredExtraVariableWebApi.hashKey(key), WiredExtraVariableWebApi.hashKey(key));
        assertNull(WiredExtraVariableWebApi.hashKey(null));
        assertNull(WiredExtraVariableWebApi.hashKey(""));
        assertNull(WiredExtraVariableWebApi.hashKey(key + "x"));
        assertNull(WiredExtraVariableWebApi.hashKey(key.substring(1) + "="));
    }

    @Test
    void theOwnerMintsKeysThatAuthenticateByHash() {
        WiredExtraVariableWebApi box = placedBox();
        Room room = room(OWNER);

        String read = box.generateKey(habbo(OWNER), room, true, 10_000);
        String write = box.generateKey(habbo(OWNER), room, false, 20_000);

        assertNotNull(read);
        assertNotNull(write);
        assertEquals(read, box.getReadKey());
        assertEquals(write, box.getWriteKey());
        assertEquals(WiredExtraVariableWebApi.Access.READ, box.authenticate(WiredExtraVariableWebApi.hashKey(read)));
        assertEquals(WiredExtraVariableWebApi.Access.WRITE, box.authenticate(WiredExtraVariableWebApi.hashKey(write)));
        assertNull(box.authenticate(WiredExtraVariableWebApi.hashKey(WiredExtraVariableWebApi.mintKey())));
        assertNull(box.authenticate(null));
    }

    @Test
    void regeneratingAKeyRevokesTheOldOne() {
        WiredExtraVariableWebApi box = placedBox();
        Room room = room(OWNER);
        String first = box.generateKey(habbo(OWNER), room, false, 10_000);
        String second = box.generateKey(habbo(OWNER), room, false, 20_000);

        assertNull(box.authenticate(WiredExtraVariableWebApi.hashKey(first)));
        assertEquals(WiredExtraVariableWebApi.Access.WRITE, box.authenticate(WiredExtraVariableWebApi.hashKey(second)));
    }

    @Test
    void onlyTheOwnerInTheirOwnRoomMintsKeys() {
        WiredExtraVariableWebApi box = placedBox();

        assertNull(box.generateKey(habbo(OWNER + 1), room(OWNER), true, 10_000));
        assertNull(box.generateKey(habbo(OWNER), room(OWNER + 1), true, 10_000));
        assertNull(box.generateKey(null, room(OWNER), true, 10_000));
        assertEquals("", box.getReadKey());
        assertFalse(box.isUsableIn(room(OWNER + 1)));
        assertTrue(box.isUsableIn(room(OWNER)));
    }

    @Test
    void generatingIsLimitedToOneRequestPerTwoSeconds() {
        WiredExtraVariableWebApi box = placedBox();
        Room room = room(OWNER);

        assertNotNull(box.generateKey(habbo(OWNER), room, true, 10_000));
        assertNull(box.generateKey(habbo(OWNER), room, false, 11_999));
        assertNotNull(box.generateKey(habbo(OWNER), room, false, 12_000));
    }

    @Test
    void aSaveKeepsOrClearsKeysButNeverSetsThem() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        Room room = room(OWNER);
        String read = box.generateKey(habbo(OWNER), room, true, 10_000);
        String write = box.generateKey(habbo(OWNER), room, false, 20_000);

        box.saveData(settings(read + "\t" + write, 1), client(OWNER));
        assertEquals(read, box.getReadKey());
        assertEquals(write, box.getWriteKey());
        assertTrue(box.isBulkDeleteAllowed());

        String chosen = WiredExtraVariableWebApi.mintKey();
        box.saveData(settings(chosen + "\t" + write, 1), client(OWNER));
        assertEquals(read, box.getReadKey());
        assertNull(box.authenticate(WiredExtraVariableWebApi.hashKey(chosen)));

        box.saveData(settings("\t" + write, 1), client(OWNER));
        assertEquals("", box.getReadKey());
        assertEquals(write, box.getWriteKey());

        box.saveData(settings("\t", 1), client(OWNER));
        assertEquals("", box.getWriteKey());
        assertFalse(box.isBulkDeleteAllowed());
    }

    @Test
    void bulkDeleteNeedsAWriteKey() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        String read = box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);

        box.saveData(settings(read + "\t", 1), client(OWNER));

        assertFalse(box.isBulkDeleteAllowed());
    }

    @Test
    void savesByAnyoneButTheOwnerChangeNothing() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        String read = box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);

        box.saveData(settings("\t", 1), client(OWNER + 1));

        assertEquals(read, box.getReadKey());
        assertFalse(box.isBulkDeleteAllowed());
    }

    @Test
    void onlyTheOwnerIsShownTheKeys() {
        WiredExtraVariableWebApi box = placedBox();
        String read = box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);
        String write = box.generateKey(habbo(OWNER), room(OWNER), false, 20_000);

        assertEquals(read + "\t" + write, settingsString(box, habbo(OWNER)));
        assertEquals("\t", settingsString(box, habbo(OWNER + 1)));
        assertEquals("\t", settingsString(box, null));

        ServerMessage plain = new ServerMessage(1);
        box.serializeWiredData(plain, room(OWNER));
        assertEquals("\t", stringParam(plain));
    }

    @Test
    void storedKeysAreBoundToTheItemSoACopiedRowLoadsWithout() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);
        String stored = box.getWiredData();

        WiredExtraVariableWebApi same = new WiredExtraVariableWebApi(1, OWNER, mock(Item.class), "", 0, 0);
        same.loadWiredData(row(stored), null);
        assertEquals(box.getReadKey(), same.getReadKey());

        WiredExtraVariableWebApi copy = new WiredExtraVariableWebApi(2, OWNER, mock(Item.class), "", 0, 0);
        copy.loadWiredData(row(stored), null);
        assertEquals("", copy.getReadKey());
        assertFalse(WiredExtraVariableWebApi.parseStored(stored, 2).hasKeys());
    }

    @Test
    void keysDieWhenTheBoxChangesOwnerByAnyRoute() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        String read = box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);
        String stored = box.getWiredData();

        box.setUserId(OWNER + 1);

        assertEquals("", box.getReadKey());
        assertNull(box.authenticate(WiredExtraVariableWebApi.hashKey(read)));
        assertEquals("\t", settingsString(box, habbo(OWNER + 1)));

        WiredExtraVariableWebApi moved = new WiredExtraVariableWebApi(1, OWNER + 1, mock(Item.class), "", 0, 0);
        moved.loadWiredData(row(stored), null);
        assertEquals("", moved.getReadKey());
        assertFalse(WiredExtraVariableWebApi.parseStored(stored, 1, OWNER + 1).hasKeys());
        assertTrue(WiredExtraVariableWebApi.parseStored(stored, 1, OWNER).hasKeys());

        box.setUserId(OWNER);
        assertEquals(read, box.getReadKey());
    }

    @Test
    void rowsWithoutAnOwnerLoadWithoutKeys() throws Exception {
        String key = WiredExtraVariableWebApi.mintKey();
        WiredExtraVariableWebApi box = new WiredExtraVariableWebApi(1, OWNER, mock(Item.class), "", 0, 0);

        box.loadWiredData(
                row("{\"itemId\":1,\"readKey\":\"" + key + "\",\"writeKey\":\"\",\"bulkDelete\":false}"), null);

        assertEquals("", box.getReadKey());
    }

    @Test
    void rowsOfTheFirstDesignLoadWithoutKeys() throws Exception {
        WiredExtraVariableWebApi box = new WiredExtraVariableWebApi(1, OWNER, mock(Item.class), "", 0, 0);
        String key = WiredExtraVariableWebApi.mintKey();

        assertDoesNotThrow(() -> box.loadWiredData(
                row("{\"variableToken\":\"42\",\"variableItemId\":42,\"readKey\":\"" + key + "\",\"writeKey\":\"" + key
                        + "\",\"writeEnabled\":true}"),
                null));
        assertEquals("", box.getReadKey());
        assertEquals("", box.getWriteKey());
        assertDoesNotThrow(() -> box.loadWiredData(row("42"), null));
        assertDoesNotThrow(() -> box.loadWiredData(row("{\"itemId\":1,\"readKey"), null));
        assertDoesNotThrow(() -> box.loadWiredData(row(null), null));
        assertEquals("", box.getReadKey());
    }

    @Test
    void pickingUpClearsKeysAndBulkDelete() throws Exception {
        WiredExtraVariableWebApi box = placedBox();
        String write = box.generateKey(habbo(OWNER), room(OWNER), false, 10_000);
        box.saveData(settings("\t" + write, 1), client(OWNER));

        box.onPickUp();

        assertEquals("", box.getWriteKey());
        assertFalse(box.isBulkDeleteAllowed());
        assertNull(box.authenticate(WiredExtraVariableWebApi.hashKey(write)));
    }

    @Test
    void theKeyStateNeverPrintsItsKeys() {
        WiredExtraVariableWebApi box = placedBox();
        String read = box.generateKey(habbo(OWNER), room(OWNER), true, 10_000);

        String printed =
                WiredExtraVariableWebApi.parseStored(box.getWiredData(), 1).toString();

        assertFalse(printed.contains(read));
    }

    @SuppressWarnings("deprecation")
    @Test
    void theOldGlobalLookupFindsNothing() {
        assertNull(WiredExtraVariableWebApi.resolve(WiredExtraVariableWebApi.mintKey()));
    }

    static WiredExtraVariableWebApi placedBox() {
        WiredExtraVariableWebApi box = new WiredExtraVariableWebApi(1, OWNER, mock(Item.class), "", 0, 0);
        box.setRoomId(ROOM);
        return box;
    }

    static Room room(int ownerId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(ROOM);
        when(room.getOwnerId()).thenReturn(ownerId);
        return room;
    }

    static Habbo habbo(int id) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static GameClient client(int id) {
        GameClient client = mock(GameClient.class);
        Habbo habbo = habbo(id);
        when(client.getHabbo()).thenReturn(habbo);
        return client;
    }

    private static WiredSettings settings(String stringParam, int bulk) {
        return new WiredSettings(new int[] {bulk}, stringParam, new int[0], 0);
    }

    private static String settingsString(WiredExtraVariableWebApi box, Habbo viewer) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredDataFor(message, room(OWNER), viewer);
        return stringParam(message);
    }

    private static String stringParam(ServerMessage message) {
        ByteBuf buffer = message.get();
        buffer.skipBytes(6);
        buffer.readBoolean();
        buffer.readInt();
        buffer.readInt();
        buffer.readInt();
        buffer.readInt();
        int length = buffer.readUnsignedShort();
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }
}
