package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitch;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.bots.BotPickupEvent;
import com.eu.habbo.messages.incoming.rooms.items.MoveWallItemEvent;
import com.eu.habbo.messages.incoming.rooms.items.RoomPickupChooserEvent;
import com.eu.habbo.messages.incoming.rooms.items.RoomPickupItemEvent;
import com.eu.habbo.messages.incoming.rooms.items.RotateMoveItemEvent;
import com.eu.habbo.messages.incoming.rooms.items.ToggleFloorItemEvent;
import com.eu.habbo.messages.incoming.rooms.items.ToggleWallItemEvent;
import com.eu.habbo.messages.incoming.rooms.items.UpdateFurniturePositionEvent;
import com.eu.habbo.messages.incoming.rooms.pets.MovePetEvent;
import com.eu.habbo.messages.incoming.rooms.pets.PetPickupEvent;
import com.eu.habbo.messages.incoming.users.SaveWiredMenuSettingsEvent;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomOwnerComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRightsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import gnu.trove.map.hash.THashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * AIR playtest mode enforced by the server: the room interactions AIR's client refuses are refused
 * here too, in playtest but not in normal play nor in the room's free furni movements mode.
 */
class RoomPlayTestEnforcementTest {
    private static final int USER = 12;
    private static final int OWNER = 7;
    private static final int ITEM = 5;

    /** Normal play, playtest, and playtest with the free furni movements mode (acc_moverotate). */
    enum Mode {
        NORMAL(false, false, false),
        PLAYTEST(true, false, true),
        PLAYTEST_FREE_MOVEMENTS(true, true, false);

        final boolean playTest;
        final boolean freeMovements;
        final boolean refused;

        Mode(boolean playTest, boolean freeMovements, boolean refused) {
            this.playTest = playTest;
            this.freeMovements = freeMovements;
            this.refused = refused;
        }
    }

    private static final class Actor {
        final Habbo habbo = mock(Habbo.class);
        final HabboInfo info = mock(HabboInfo.class);
        final HabboStats stats = mock(HabboStats.class);
        final RoomUnit unit = mock(RoomUnit.class);
        final GameClient client = mock(GameClient.class);
        final Room room;

        Actor(Mode mode, Room room) {
            this(mode.playTest, mode.freeMovements, room);
        }

        Actor(boolean playTest, boolean freeMovements, Room room) {
            this.room = room;
            when(this.habbo.getHabboInfo()).thenReturn(this.info);
            when(this.habbo.getHabboStats()).thenReturn(this.stats);
            when(this.habbo.getRoomUnit()).thenReturn(this.unit);
            when(this.habbo.getClient()).thenReturn(this.client);
            when(this.habbo.hasPermission(Permission.ACC_MOVEROTATE)).thenReturn(freeMovements);
            when(this.info.getId()).thenReturn(USER);
            when(this.info.getCurrentRoom()).thenReturn(room);
            when(this.stats.isPlayTestMode()).thenReturn(playTest);
            setCache(this.stats);
            when(this.client.getHabbo()).thenReturn(this.habbo);
        }

        void handle(MessageHandler handler, Packet packet) throws Exception {
            handler.client = this.client;
            handler.packet = new ClientMessage(0, packet.buffer);
            handler.handle();
        }
    }

    private static final class Packet {
        final ByteBuf buffer = Unpooled.buffer();

        Packet integer(int value) {
            this.buffer.writeInt(value);
            return this;
        }

        Packet bool(boolean value) {
            this.buffer.writeBoolean(value);
            return this;
        }

        Packet string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            this.buffer.writeShort(bytes.length);
            this.buffer.writeBytes(bytes);
            return this;
        }
    }

    private static void setCache(HabboStats stats) {
        try {
            Field cache = HabboStats.class.getDeclaredField("cache");
            cache.setAccessible(true);
            cache.set(stats, new THashMap<String, Object>());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn("furni");
        return base;
    }

    private static HabboItem floorItem(Room room) {
        HabboItem item = mock(HabboItem.class);
        Item itemBase = base();
        when(item.getBaseItem()).thenReturn(itemBase);
        when(room.getHabboItem(ITEM)).thenReturn(item);
        return item;
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(OWNER);
        when(room.hasRights(any(Habbo.class))).thenReturn(true);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        return room;
    }

    // --- moving and rotating ---

    @ParameterizedTest
    @EnumSource(Mode.class)
    void floorFurniIsMovedAndRotatedOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        floorItem(room);
        Actor actor = new Actor(mode, room);

        actor.handle(
                new RotateMoveItemEvent(),
                new Packet().integer(ITEM).integer(1).integer(2).integer(2));

        verify(room, mode.refused ? never() : times(1)).getLayout();
        verify(actor.client, mode.refused ? times(1) : never()).sendResponse(any(FloorItemUpdateComposer.class));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void theBuilderToolMoveFollowsTheSameRule(Mode mode) throws Exception {
        Room room = room();
        floorItem(room);
        Actor actor = new Actor(mode, room);

        actor.handle(
                new UpdateFurniturePositionEvent(),
                new Packet().integer(ITEM).integer(1).integer(2).integer(0).integer(2));

        verify(room, mode.refused ? never() : times(1)).getLayout();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void wallFurniIsMovedOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        HabboItem item = floorItem(room);
        String position = ":w=1,2 l=3,4 l";
        when(item.getWallPosition()).thenReturn(position);
        Actor actor = new Actor(mode, room);

        actor.handle(new MoveWallItemEvent(), new Packet().integer(ITEM).string(position));

        verify(item, mode.refused ? never() : times(1)).setWallPosition(position);
        verify(room, mode.refused ? never() : times(1)).updateItem(item);
    }

    // --- picking up and ejecting ---

    @ParameterizedTest
    @EnumSource(Mode.class)
    void ownFurniIsPickedUpOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        HabboItem item = floorItem(room);
        when(item.getUserId()).thenReturn(USER);
        Actor actor = new Actor(mode, room);

        actor.handle(new RoomPickupItemEvent(), new Packet().integer(10).integer(ITEM));

        verify(room, mode.refused ? never() : times(1)).pickUpItem(item, actor.habbo);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void someoneElsesFurniIsEjectedOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        HabboItem item = floorItem(room);
        when(item.getUserId()).thenReturn(99);
        Actor actor = new Actor(mode, room);

        actor.handle(new RoomPickupItemEvent(), new Packet().integer(10).integer(ITEM));

        verify(room, mode.refused ? never() : times(1)).ejectUserItem(item);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void theFurniChooserPicksUpOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        HabboItem item = floorItem(room);
        when(item.getUserId()).thenReturn(USER);
        Actor actor = new Actor(mode, room);

        actor.handle(new RoomPickupChooserEvent(), new Packet().integer(1).integer(ITEM));

        verify(room, mode.refused ? never() : times(1)).pickUpItem(item, actor.habbo);
    }

    // --- pets and bots ---

    @ParameterizedTest
    @EnumSource(Mode.class)
    void petsAreMovedAndRotatedOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        Pet pet = mock(Pet.class);
        when(pet.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(room.getPet(3)).thenReturn(pet);
        Actor actor = new Actor(mode, room);

        actor.handle(
                new MovePetEvent(),
                new Packet().integer(3).integer(1).integer(1).integer(2));

        verify(room, mode.refused ? never() : times(1)).getLayout();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void petsArePickedUpOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        Actor actor = new Actor(mode, room);

        actor.handle(new PetPickupEvent(), new Packet().integer(3));

        verify(room, mode.refused ? never() : times(1)).getPet(3);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void botsArePickedUpOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        Actor actor = new Actor(mode, room);
        BotManager bots = mock(BotManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getBotManager()).thenReturn(bots);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            actor.handle(new BotPickupEvent(), new Packet().integer(4));
        }

        verify(bots, mode.refused ? never() : times(1)).pickUpBot(4, actor.habbo);
    }

    // --- using furni ---

    @ParameterizedTest
    @EnumSource(Mode.class)
    void aRightsOnlyFurniAndAWiredBoxAreUsedOnlyOutsidePlaytest(Mode mode) throws Exception {
        Room room = room();
        InteractionWiredTrigger wired = mock(InteractionWiredTrigger.class);
        when(wired.isUsable()).thenReturn(true);
        Item wiredBase = base();
        when(wired.getBaseItem()).thenReturn(wiredBase);
        when(room.getHabboItem(ITEM)).thenReturn(wired);
        Actor actor = new Actor(mode, room);

        toggleFloor(actor);

        verify(wired, mode.refused ? never() : times(1)).onClick(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void furniEverybodyMayUseIsUsedInEveryMode(Mode mode) throws Exception {
        Room room = room();
        InteractionSwitch lever = mock(InteractionSwitch.class);
        Item leverBase = base();
        when(lever.getBaseItem()).thenReturn(leverBase);
        when(room.getHabboItem(ITEM)).thenReturn(lever);
        Actor actor = new Actor(mode, room);

        toggleFloor(actor);

        verify(lever).onClick(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void wallFurniFollowTheirUsagePolicyToo(Mode mode) throws Exception {
        Room room = room();
        HabboItem lamp = mock(HabboItem.class);
        when(lamp.isUsable()).thenReturn(true);
        Item lampBase = base();
        when(lamp.getBaseItem()).thenReturn(lampBase);
        InteractionSwitch wallSwitch = mock(InteractionSwitch.class);
        Item wallSwitchBase = base();
        when(wallSwitch.getBaseItem()).thenReturn(wallSwitchBase);
        when(room.getHabboItem(ITEM)).thenReturn(lamp);
        when(room.getHabboItem(ITEM + 1)).thenReturn(wallSwitch);
        Actor actor = new Actor(mode, room);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));

            actor.handle(new ToggleWallItemEvent(), new Packet().integer(ITEM).integer(0));
            actor.handle(
                    new ToggleWallItemEvent(), new Packet().integer(ITEM + 1).integer(0));
        }

        verify(lamp, mode.refused ? never() : times(1)).onClick(any(), any(), any());
        verify(wallSwitch).onClick(any(), any(), any());
    }

    private static void toggleFloor(Actor actor) throws Exception {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));

            actor.handle(new ToggleFloorItemEvent(), new Packet().integer(ITEM).integer(0));
        }
    }

    // --- the controller level sent to the client ---

    @Test
    void theOwnerIsSentLevelZeroInPlaytestButStaysTheOwner() {
        Room room = new Room(1, OWNER);
        Actor owner = ownerOf(room, true, false);

        room.refreshRightsForHabbo(owner.habbo);

        assertEquals(RoomRightLevels.NONE, sentLevel(owner));
        verify(owner.client).sendResponse(any(RoomOwnerComposer.class));
        verify(owner.unit).setRightsLevel(RoomRightLevels.MODERATOR);
    }

    @Test
    void theOwnerIsSentTheRealLevelOutsidePlaytest() {
        Room room = new Room(1, OWNER);
        Actor owner = ownerOf(room, false, false);

        room.refreshRightsForHabbo(owner.habbo);

        assertEquals(RoomRightLevels.MODERATOR, sentLevel(owner));
    }

    @Test
    void aRightsHolderIsSentLevelZeroInPlaytestEvenWithFreeMovements() {
        Room room = new Room(1, OWNER);
        room.getRightsManager().getRights().add(USER);

        Actor normal = new Actor(false, false, room);
        room.refreshRightsForHabbo(normal.habbo);
        assertEquals(RoomRightLevels.RIGHTS, sentLevel(normal));

        Actor playTest = new Actor(true, true, room);
        room.refreshRightsForHabbo(playTest.habbo);
        assertEquals(RoomRightLevels.NONE, sentLevel(playTest));
        verify(playTest.unit).setRightsLevel(RoomRightLevels.RIGHTS);
    }

    private static Actor ownerOf(Room room, boolean playTest, boolean freeMovements) {
        Actor owner = new Actor(playTest, freeMovements, room);
        when(owner.info.getId()).thenReturn(OWNER);
        return owner;
    }

    private static RoomRightLevels sentLevel(Actor actor) {
        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(actor.client, atLeastOnce()).sendResponse(sent.capture());
        List<RoomRightLevels> levels = sent.getAllValues().stream()
                .filter(RoomRightsComposer.class::isInstance)
                .map(composer -> ((RoomRightsComposer) composer).getType())
                .toList();
        assertEquals(1, levels.size(), "one rights update");
        return levels.get(0);
    }

    // --- the switch ---

    @Test
    void switchingPlaytestOnOrOffSendsTheRightsAgainAtOnce() throws Exception {
        Room room = room();
        Actor actor = new Actor(false, false, room);
        when(actor.stats.isPlayTestMode()).thenReturn(false, true);

        actor.handle(new SaveWiredMenuSettingsEvent(), preferences(true));

        verify(actor.stats).setWiredMenuPreferences("1,1,1,0,default");
        verify(room).refreshRightsForHabbo(actor.habbo);

        Actor off = new Actor(true, false, room);
        when(off.stats.isPlayTestMode()).thenReturn(true, false);

        off.handle(new SaveWiredMenuSettingsEvent(), preferences(false));

        verify(room).refreshRightsForHabbo(off.habbo);
    }

    @Test
    void savingOtherPreferencesLeavesTheRightsAlone() throws Exception {
        Room room = room();
        Actor actor = new Actor(true, false, room);

        actor.handle(new SaveWiredMenuSettingsEvent(), preferences(true));

        verify(room, never()).refreshRightsForHabbo(any());
    }

    private static Packet preferences(boolean playTest) {
        return new Packet()
                .bool(true)
                .bool(true)
                .bool(playTest)
                .integer(0)
                .bool(false)
                .bool(false)
                .string("default");
    }

    // --- the rule itself ---

    @Test
    void theRuleIgnoresUsersWithoutTheSwitch() {
        assertFalse(RoomPlayTestSupport.isPlayTestMode(null));
        assertFalse(RoomPlayTestSupport.isRestricted(null));
        assertEquals(RoomRightLevels.GUILD_ADMIN, RoomPlayTestSupport.reportedLevel(null, RoomRightLevels.GUILD_ADMIN));

        Actor freeMovements = new Actor(true, true, room());
        assertTrue(RoomPlayTestSupport.isPlayTestMode(freeMovements.habbo));
        assertFalse(RoomPlayTestSupport.isRestricted(freeMovements.habbo));
        assertTrue(RoomPlayTestSupport.hasFreeFurniMovementsMode(freeMovements.habbo));
    }
}
