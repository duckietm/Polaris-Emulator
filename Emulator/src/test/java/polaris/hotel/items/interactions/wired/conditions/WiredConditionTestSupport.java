package polaris.hotel.items.interactions.wired.conditions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.wired.api.IWiredCondition;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** The little world a condition needs: a room, one user standing in it, and a wired context. */
final class WiredConditionTestSupport {

    private WiredConditionTestSupport() {}

    static Item boxBase() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }

    /** A user with this id and rank, known to the room through their room unit. */
    static RoomUnit user(Room room, int userId, int rankId) {
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Rank rank = mock(Rank.class);
        when(unit.getId()).thenReturn(userId);
        when(rank.getId()).thenReturn(rankId);
        when(info.getId()).thenReturn(userId);
        when(info.getRank()).thenReturn(rank);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getRoomUnit()).thenReturn(unit);
        when(room.getUser(unit)).thenReturn(user);
        when(room.getUser(userId)).thenReturn(user);
        return unit;
    }

    /** A condition that passes and is recorded, as when its stack runs. */
    static boolean passes(IWiredCondition condition, WiredContext ctx) {
        if (!condition.evaluate(ctx)) return false;
        condition.commit(ctx);
        return true;
    }

    static WiredContext context(Room room, RoomUnit actor) {
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.CUSTOM, room);
        if (actor != null) builder.actor(actor);
        return new WiredContext(builder.build(), null, mock(WiredServices.class), new WiredState(20));
    }

    static WiredSettings settings(int[] ints, int... furniIds) {
        return new WiredSettings(ints, "", furniIds, 0);
    }

    /** A condition save with {@code quantifier} in the save packet's quantifier slot. */
    static WiredSettings quantified(int[] ints, int quantifier) {
        WiredSettings settings = settings(ints);
        settings.setQuantifierCode(quantifier);
        return settings;
    }
}
