package com.eu.habbo.habbohotel.wired.variablefx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFxHealthPoints;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxConfigsComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxStatusComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxStatusRemovedComposer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * One flush sends every viewer the configs they lack and the statuses that changed for them, and
 * only to those the box's visibility lets see the holder.
 */
class WiredVariableFxServiceTest {

    private static final int DEFINITION_ID = 20;

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    /** A player in the room with a client that records what it is sent. */
    private static final class Player {
        final Habbo habbo = mock(Habbo.class);
        final GameClient client = mock(GameClient.class);
        final List<MessageComposer> sent = new ArrayList<>();
        final int userId;

        Player(int userId, int roomUnitId, GameTeamColors team) {
            this.userId = userId;
            HabboInfo info = mock(HabboInfo.class);
            when(info.getId()).thenReturn(userId);
            if (team != null) {
                GamePlayer player = mock(GamePlayer.class);
                when(player.getTeamColor()).thenReturn(team);
                when(info.getGamePlayer()).thenReturn(player);
            }
            RoomUnit unit = mock(RoomUnit.class);
            when(unit.getId()).thenReturn(roomUnitId);
            when(this.habbo.getHabboInfo()).thenReturn(info);
            when(this.habbo.getRoomUnit()).thenReturn(unit);
            when(this.habbo.getClient()).thenReturn(this.client);
            Mockito.doAnswer(invocation -> {
                        this.sent.add(invocation.getArgument(0));
                        return null;
                    })
                    .when(this.client)
                    .sendResponse(any(MessageComposer.class));
        }

        <T> List<T> sentOf(Class<T> type) {
            List<T> matches = new ArrayList<>();
            for (MessageComposer composer : this.sent) {
                if (type.isInstance(composer)) matches.add(type.cast(composer));
            }
            return matches;
        }
    }

    private static final class Fixture {
        final Room room = mock(Room.class);
        final RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        final RoomUserVariableManager userVariables = mock(RoomUserVariableManager.class);
        final RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
        final List<Habbo> habbos = new ArrayList<>();
        final WiredExtraVariableFxHealthPoints box = new WiredExtraVariableFxHealthPoints(7, 1, base(), "", 0, 0);
        final WiredExtraUserVariable variable = new WiredExtraUserVariable(DEFINITION_ID, 1, base(), "", 0, 0);

        Fixture(int visibility) throws Exception {
            when(this.room.getId()).thenReturn(81);
            when(this.room.getRoomSpecialTypes()).thenReturn(this.specialTypes);
            when(this.room.getUserVariableManager()).thenReturn(this.userVariables);
            when(this.room.getFurniVariableManager()).thenReturn(this.furniVariables);
            when(this.room.getHabbos()).thenReturn(this.habbos);
            when(this.room.getFloorItems()).thenReturn(Set.of());

            this.box.setX((short) 2);
            this.box.setY((short) 3);
            this.box.saveData(
                    new WiredSettings(
                            new int[] {
                                WiredExtraVariableFx.SOURCE_USER,
                                visibility,
                                0,
                                3000,
                                0,
                                -1,
                                2,
                                0,
                                0,
                                100,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                            },
                            "",
                            new int[0],
                            0),
                    null);
            Set<InteractionWiredExtra> extras = new LinkedHashSet<>();
            extras.add(this.box);
            extras.add(this.variable);
            when(this.specialTypes.getExtras()).thenReturn(extras);
            when(this.specialTypes.getExtras(2, 3)).thenReturn(extras);
        }

        Player join(int userId, int roomUnitId, GameTeamColors team, Integer value) {
            Player player = new Player(userId, roomUnitId, team);
            this.habbos.add(player.habbo);
            if (value != null) {
                when(this.userVariables.hasVariable(userId, DEFINITION_ID)).thenReturn(true);
                when(this.userVariables.getCurrentValue(userId, DEFINITION_ID)).thenReturn(value);
            }
            return player;
        }
    }

    @Test
    void aNewcomerGetsTheConfigsAndAFullStatusSync() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_EVERYONE);
        Player holder = fixture.join(1, 100, null, 60);
        Player watcher = fixture.join(2, 101, null, null);
        WiredVariableFxService service = new WiredVariableFxService(81);

        service.flush(fixture.room);

        for (Player player : List.of(holder, watcher)) {
            assertEquals(2, player.sent.size(), "user " + player.userId);
            assertInstanceOf(WiredVariableFxConfigsComposer.class, player.sent.get(0));
            assertInstanceOf(WiredVariableFxStatusComposer.class, player.sent.get(1));
        }
    }

    @Test
    void nothingGoesOutWhileNothingChangesAndAChangeGoesOutOnce() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_EVERYONE);
        Player holder = fixture.join(1, 100, null, 60);
        WiredVariableFxService service = new WiredVariableFxService(81);
        service.flush(fixture.room);
        holder.sent.clear();

        service.flush(fixture.room);
        assertTrue(holder.sent.isEmpty());

        when(fixture.userVariables.getCurrentValue(1, DEFINITION_ID)).thenReturn(59);
        service.flush(fixture.room);
        assertEquals(1, holder.sentOf(WiredVariableFxStatusComposer.class).size());
        assertTrue(holder.sentOf(WiredVariableFxConfigsComposer.class).isEmpty());
    }

    @Test
    void aHolderWhoLosesTheVariableIsRemovedFromEveryViewer() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_EVERYONE);
        Player holder = fixture.join(1, 100, null, 60);
        Player watcher = fixture.join(2, 101, null, null);
        WiredVariableFxService service = new WiredVariableFxService(81);
        service.flush(fixture.room);
        holder.sent.clear();
        watcher.sent.clear();

        when(fixture.userVariables.hasVariable(1, DEFINITION_ID)).thenReturn(false);
        service.flush(fixture.room);

        assertEquals(
                1, holder.sentOf(WiredVariableFxStatusRemovedComposer.class).size());
        assertEquals(
                1, watcher.sentOf(WiredVariableFxStatusRemovedComposer.class).size());
    }

    @Test
    void onlyTheHolderSeesAnOnlyUserFx() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_ONLY_USER);
        Player holder = fixture.join(1, 100, null, 60);
        Player watcher = fixture.join(2, 101, null, null);
        WiredVariableFxService service = new WiredVariableFxService(81);

        service.flush(fixture.room);

        assertEquals(1, holder.sentOf(WiredVariableFxStatusComposer.class).size());
        assertTrue(watcher.sentOf(WiredVariableFxStatusComposer.class).isEmpty());
        // The config still goes to everyone: it is harmless without statuses and saves a resync later.
        assertEquals(1, watcher.sentOf(WiredVariableFxConfigsComposer.class).size());
    }

    @Test
    void aTeamFxIsSeenByTeamMatesAndTheHolder() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_GAME_TEAM);
        Player holder = fixture.join(1, 100, GameTeamColors.RED, 60);
        Player mate = fixture.join(2, 101, GameTeamColors.RED, null);
        Player rival = fixture.join(3, 102, GameTeamColors.BLUE, null);
        Player spectator = fixture.join(4, 103, null, null);
        WiredVariableFxService service = new WiredVariableFxService(81);

        service.flush(fixture.room);

        assertEquals(1, holder.sentOf(WiredVariableFxStatusComposer.class).size());
        assertEquals(1, mate.sentOf(WiredVariableFxStatusComposer.class).size());
        assertTrue(rival.sentOf(WiredVariableFxStatusComposer.class).isEmpty());
        assertTrue(spectator.sentOf(WiredVariableFxStatusComposer.class).isEmpty());
    }

    @Test
    void anImportedHasVariableAudienceIsReadThroughTheImportingRoomsReference() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_EVERYONE);
        Player member = fixture.join(1, 100, null, 60);
        fixture.join(2, 101, null, 60);
        WiredExtraVariableFxHealthPoints imported = new WiredExtraVariableFxHealthPoints(8, 1, base(), "", 0, 0);
        imported.saveData(
                new WiredSettings(
                        new int[] {
                            WiredExtraVariableFx.SOURCE_USER,
                            WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE,
                            0,
                            3000,
                            0,
                            -1,
                            2,
                            0,
                            0,
                            100,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                        },
                        "\t\tcustom:300\t",
                        new int[0],
                        0),
                null);
        // The source room's audience variable 300 is reference 950 here; only the member holds it.
        when(fixture.userVariables.hasVariable(1, 950)).thenReturn(true);

        assertEquals(
                Set.of(),
                WiredVariableFxService.audienceOf(fixture.room, imported, IntUnaryOperator.identity(), fixture.habbos),
                "the source room's id names nothing here");
        assertEquals(
                Set.of(member.userId),
                WiredVariableFxService.audienceOf(fixture.room, imported, id -> id == 300 ? 950 : id, fixture.habbos));
    }

    @Test
    void aViewerWhoLeavesAndReturnsIsSyncedFromScratch() throws Exception {
        Fixture fixture = new Fixture(WiredExtraVariableFx.VISIBILITY_EVERYONE);
        Player holder = fixture.join(1, 100, null, 60);
        WiredVariableFxService service = new WiredVariableFxService(81);
        service.flush(fixture.room);

        fixture.habbos.clear();
        service.flush(fixture.room);
        fixture.habbos.add(holder.habbo);
        holder.sent.clear();

        service.flush(fixture.room);

        assertEquals(1, holder.sentOf(WiredVariableFxConfigsComposer.class).size());
        assertEquals(1, holder.sentOf(WiredVariableFxStatusComposer.class).size());
        assertTrue(holder.sentOf(WiredVariableFxStatusRemovedComposer.class).isEmpty());
    }
}
