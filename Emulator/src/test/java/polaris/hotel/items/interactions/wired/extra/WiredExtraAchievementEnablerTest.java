package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredLargePayload;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.wired.WiredEnvironmentComposer;

/** Habbo's achievement enabler add-on: which achievements the room's wired may progress. */
class WiredExtraAchievementEnablerTest {

    private static WiredExtraAchievementEnabler box() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(5151);
        return new WiredExtraAchievementEnabler(21, 1, base, "", 0, 0);
    }

    private static GameClient author(boolean superwired) {
        GameClient client = mock(GameClient.class);
        User user = mock(User.class);
        when(client.getUser()).thenReturn(user);
        when(user.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(superwired);
        return client;
    }

    private static WiredSettings settings(String text) {
        return new WiredSettings(new int[0], text, new int[0], 0);
    }

    private static Room room(InteractionWiredExtra... extras) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getExtras()).thenReturn(new LinkedHashSet<>(List.of(extras)));
        when(specialTypes.getEffects()).thenReturn(Set.of());
        when(specialTypes.getTriggers()).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        return room;
    }

    @Test
    void savingItNeedsTheWiredRewardPermission() {
        WiredExtraAchievementEnabler box = box();

        assertFalse(box.saveData(settings("ACH_Game"), author(false)), "a player cannot declare achievements");
        assertFalse(box.saveData(settings("ACH_Game"), null));
        assertTrue(box.getAchievements().isEmpty());
        assertTrue(box.saveData(settings("ACH_Game"), author(true)));
        assertEquals(List.of("ACH_Game"), box.getAchievements());
    }

    @Test
    void theTextIsSplitIntoValidDistinctNames() {
        assertEquals(
                List.of("ACH_Game", "ACH_Race", "bad", "Tag-2"),
                WiredExtraAchievementEnabler.parseNames(" ACH_Game,ACH_Race\n ACH_Game; bad name! Tag-2 <script> "));
        assertEquals(List.of(), WiredExtraAchievementEnabler.parseNames(null));
        assertEquals(List.of(), WiredExtraAchievementEnabler.parseNames("x".repeat(65)));
    }

    @Test
    void theTextAndTheNamesAreBounded() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            many.append("A").append(i).append(' ');
        }
        assertEquals(
                WiredExtraAchievementEnabler.MAX_NAMES,
                WiredExtraAchievementEnabler.parseNames(many.toString()).size());

        String cut = "A".repeat(10) + " ".repeat(WiredExtraAchievementEnabler.MAX_TEXT_LENGTH) + "Late";
        assertEquals(
                List.of("AAAAAAAAAA"), WiredExtraAchievementEnabler.parseNames(cut), "past 2000 characters is ignored");
        assertTrue(box() instanceof WiredLargePayload, "the 2000-character editor text fits the save packet");
    }

    @Test
    void theDialogGetsTheNamesUnderItsOwnCode() {
        WiredExtraAchievementEnabler box = box();
        box.saveData(settings("ACH_Game ACH_Race"), author(true));

        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, null);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readBoolean();
            buffer.readInt();
            buffer.readInt();
            assertEquals(5151, buffer.readInt());
            assertEquals(21, buffer.readInt());
            byte[] text = new byte[buffer.readShort()];
            buffer.readBytes(text);
            assertEquals("ACH_Game\nACH_Race", new String(text, StandardCharsets.UTF_8));
            assertEquals(0, buffer.readInt());
            buffer.readInt();
            assertEquals(WiredExtraAchievementEnabler.CODE, buffer.readInt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void theNamesSurviveTheDatabaseAndBadRowsLoadEmpty() throws Exception {
        WiredExtraAchievementEnabler saved = box();
        saved.saveData(settings("ACH_Game,ACH_Race"), author(true));

        WiredExtraAchievementEnabler loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertEquals(List.of("ACH_Game", "ACH_Race"), loaded.getAchievements());

        WiredExtraAchievementEnabler tampered = box();
        tampered.loadWiredData(row("{\"achievements\":[\"ok\",\"not ok\",\"" + "y".repeat(100) + "\"]}"), null);
        assertEquals(List.of("ok"), tampered.getAchievements());

        WiredExtraAchievementEnabler broken = box();
        broken.loadWiredData(row("{nope"), null);
        assertTrue(broken.getAchievements().isEmpty());

        loaded.onPickUp();
        assertTrue(loaded.getAchievements().isEmpty());
    }

    @Test
    void theRoomDeclaresWhatItsEnablersName() {
        WiredExtraAchievementEnabler first = box();
        first.saveData(settings("ACH_Game"), author(true));
        WiredExtraAchievementEnabler second = box();
        second.saveData(settings("ACH_Race ACH_Game"), author(true));

        assertEquals(
                Set.of("ACH_Game", "ACH_Race"), WiredExtraAchievementEnabler.declaredAchievements(room(first, second)));
        assertEquals(Set.of(), WiredExtraAchievementEnabler.declaredAchievements(room()));
        assertEquals(Set.of(), WiredExtraAchievementEnabler.declaredAchievements(null));
    }

    @Test
    void wiredEnvironmentListsOnlyTheNamesTheHotelAllows() {
        WiredExtraAchievementEnabler enabler = box();
        enabler.saveData(settings("ACH_Game ACH_RoomEntry"), author(true));
        Room room = room(enabler);

        assertEquals(List.of(), environment(room, null), "off without a configuration");

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean("hotel.wired.achievements.enabled", false)).thenReturn(true);
        when(config.getValue("hotel.wired.achievements.allowed", "")).thenReturn("ACH_Game");
        when(config.getInt(Mockito.anyString(), Mockito.anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        assertEquals(List.of("ACH_Game"), environment(room, config));
    }

    private static List<String> environment(Room room, ConfigurationManager config) {
        try (MockedStatic<WiredPlatform> platform = Mockito.mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::configuration).thenReturn(config);
            ByteBuf packet = new WiredEnvironmentComposer(room).compose().get();
            try {
                packet.skipBytes(6);
                packet.readBoolean();
                List<String> names = new ArrayList<>();
                int count = packet.readInt();
                for (int i = 0; i < count; i++) {
                    byte[] text = new byte[packet.readShort()];
                    packet.readBytes(text);
                    names.add(new String(text, StandardCharsets.UTF_8));
                }
                return names;
            } finally {
                packet.release();
            }
        }
    }

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }
}
