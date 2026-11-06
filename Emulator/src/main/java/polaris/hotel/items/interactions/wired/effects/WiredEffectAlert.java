package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;

public class WiredEffectAlert extends WiredEffectWhisper {
    public WiredEffectAlert(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAlert(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> sourceUsers = resolveUsers(ctx);
        List<User> recipients = resolveRecipients(ctx, sourceUsers);
        User sharedSourceUser = (this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS)
                ? resolveMessageSourceUser(ctx, sourceUsers)
                : null;

        for (User user : recipients) {
            if (!shouldDeliverToRecipient(ctx, user)) {
                continue;
            }

            User referenceUser = (sharedSourceUser != null) ? sharedSourceUser : user;
            String username = (referenceUser != null && referenceUser.getUserInfo() != null)
                    ? referenceUser.getUserInfo().getUsername()
                    : "";

            String message = this.message
                    .replace(
                            "%online%",
                            Emulator.getGameEnvironment().getUserManager().getOnlineCount() + "")
                    .replace("%username%", username)
                    .replace(
                            "%roomsloaded%",
                            Emulator.getGameEnvironment().getRoomManager().loadedRoomsCount() + "");
            user.alert(WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, message));
        }
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.EFFECT_MESSAGE;
    }
}
