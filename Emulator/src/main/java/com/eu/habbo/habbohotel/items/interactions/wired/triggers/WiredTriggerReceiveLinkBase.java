package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.link.WiredLinkReceiver;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The official receive link triggers: the picked furni are the WIRED Links ({@code
 * wiredfurni.params.link.endpoints}) the stack listens on. Picks that are not a Link are dropped.
 */
public abstract class WiredTriggerReceiveLinkBase extends InteractionWiredTrigger implements WiredLinkReceiver {
    protected final Set<HabboItem> links = new LinkedHashSet<>();

    protected WiredTriggerReceiveLinkBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredTriggerReceiveLinkBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean listensTo(HabboItem link) {
        if (link == null) return false;
        for (HabboItem picked : this.links) {
            if (picked != null && picked.getId() == link.getId()) return true;
        }
        return false;
    }

    /** The event's Link is one of the picks. */
    protected boolean matchesLink(WiredEvent event) {
        return event.getSourceItem().map(this::listensTo).orElse(false);
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    /** Keeps the picked Links; the caller saves its own settings first. */
    protected void savePicks(WiredSettings settings) {
        Room room = settings.getRoom();
        if (room == null && WiredPlatform.gameEnvironment() != null) {
            room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        }
        this.links.clear();
        if (room == null) return;
        for (int id : settings.getFurniIds()) {
            if (this.links.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
            HabboItem item = room.getHabboItem(id);
            if (InteractionWiredLink.isLink(item)) this.links.add(item);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.savePicks(settings);
        return true;
    }

    protected List<Integer> linkIds() {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : this.links) ids.add(item.getId());
        return ids;
    }

    protected void loadLinks(List<Integer> ids, Room room) {
        this.links.clear();
        if (ids == null || room == null) return;
        for (Integer id : ids) {
            if (id == null) continue;
            HabboItem item = room.getHabboItem(id);
            if (InteractionWiredLink.isLink(item)) this.links.add(item);
        }
    }

    /** Writes the legacy definition with the box's string and int params. */
    protected void serialize(ServerMessage message, Room room, String stringParam, int[] intParams) {
        this.links.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.links.size());
        for (HabboItem item : this.links) message.appendInt(BuildersClubItemIds.toWire(item.getId()));
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(BuildersClubItemIds.toWire(this.getId()));
        message.appendString(stringParam);
        message.appendInt(intParams.length);
        for (int value : intParams) message.appendInt(value);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.links.clear();
    }
}
