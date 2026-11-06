package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.WiredCompatibilityDiagnostics;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;

public class WiredEffectGiveHandItem extends WiredEffectWhisper {
    public WiredEffectGiveHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveHandItem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        try {
            int itemId = Math.max(0, Integer.parseInt(this.message));

            Room room = ctx.room();

            for (polaris.hotel.rooms.RoomUnit unit : resolveUsers(ctx)) {
                User user = room.getUser(unit);
                if (user != null) {
                    room.giveHandItem(user, itemId);
                }
            }
        } catch (Exception e) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_GIVE_HAND_ITEM,
                    ctx.room().getId(),
                    this.getId(),
                    e);
        }
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.GIVE_HAND_ITEM;
    }
}
