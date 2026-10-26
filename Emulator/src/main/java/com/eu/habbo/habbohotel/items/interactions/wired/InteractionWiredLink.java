package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOpenComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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
        if (client == null || room == null || client.getHabbo() == null) {
            return;
        }
        Habbo habbo = client.getHabbo();
        if (habbo.getHabboInfo().getId() == this.getUserId()) {
            client.sendResponse(new WiredLinkOpenComposer(this.getId()));
            return;
        }
        if (room.hasRights(habbo)) {
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
    public static boolean isLink(HabboItem item) {
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
            HabboItem item = room.getHabboItem(id);
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
