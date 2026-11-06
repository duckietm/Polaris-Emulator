package polaris.hotel.items.interactions.wired;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.wired.WiredLinkOpenComposer;

/**
 * The WIRED Link furni ({@code wf_link}). Using it opens the network window for its owner (AIR
 * {@code WiredLinkController.onOpen}); the send and receive link boxes pick it as their endpoint.
 * Picking it up takes it off its network, as the official connections hint says.
 */
public class InteractionWiredLink extends InteractionDefault {
    public static final String INTERACTION = "wf_link";
    public static final String NOT_OWNER_KEY = "wiredfurni.error.link.not_owner";

    public InteractionWiredLink(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredLink(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        if (client == null || room == null || client.getUser() == null) {
            return;
        }
        User user = client.getUser();
        if (user.getUserInfo().getId() == this.getUserId()) {
            client.sendResponse(new WiredLinkOpenComposer(this.getId()));
            return;
        }
        if (room.hasRights(user)) {
            Map<String, String> keys = new HashMap<>();
            keys.put("message", "${" + NOT_OWNER_KEY + "}");
            client.sendResponse(new BubbleAlertComposer("wired.error", keys));
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        WiredLinkService.get().removeLink(this.getUserId(), this.getId());
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    /** Whether the furni is a WIRED Link. */
    public static boolean isLink(UserItem item) {
        return item instanceof InteractionWiredLink;
    }

    public static final String REQUIRE_LINK_FURNI = "wiredfurni.error.require_link_furni";

    /**
     * AIR refuses a link box's picks when one of them is a furni that is not a Link; an id that names no furni is
     * not checked.
     */
    public static void refuseNonLinkPicks(Room room, int[] furniIds) {
        if (room == null || furniIds == null) {
            return;
        }

        for (int id : furniIds) {
            UserItem item = room.getUserItem(id);
            if (item != null && !isLink(item)) {
                throw new WiredSaveRefusedException(REQUIRE_LINK_FURNI);
            }
        }
    }

    /** Habbo's Link lights up when a message goes through it: its state flips, as an antenna's does. */
    public void pulse(Room room) {
        if (room == null || this.getBaseItem() == null || this.getBaseItem().getStateCount() <= 1) {
            return;
        }
        synchronized (this) {
            this.setExtradata("1".equals(this.getExtradata()) ? "0" : "1");
        }
        room.updateItemState(this);
    }
}
