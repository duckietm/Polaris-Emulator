package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.WiredPlatform;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.link.WiredLinkReceiver;
import polaris.messages.ServerMessage;

/**
 * The official receive link triggers: the picked furni are the WIRED Links ({@code
 * wiredfurni.params.link.endpoints}) the stack listens on. Picks that are not a Link are dropped.
 */
public abstract class WiredTriggerReceiveLinkBase extends InteractionWiredTrigger implements WiredLinkReceiver {
    protected final Set<UserItem> links = new LinkedHashSet<>();

    protected WiredTriggerReceiveLinkBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredTriggerReceiveLinkBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean listensTo(UserItem link) {
        if (link == null) return false;
        for (UserItem picked : this.links) {
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
        InteractionWiredLink.refuseNonLinkPicks(room, settings.getFurniIds());
        this.links.clear();
        if (room == null) return;
        for (int id : settings.getFurniIds()) {
            if (this.links.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
            UserItem item = room.getUserItem(id);
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
        for (UserItem item : this.links) ids.add(item.getId());
        return ids;
    }

    protected void loadLinks(List<Integer> ids, Room room) {
        this.links.clear();
        if (ids == null || room == null) return;
        for (Integer id : ids) {
            if (id == null) continue;
            UserItem item = room.getUserItem(id);
            if (InteractionWiredLink.isLink(item)) this.links.add(item);
        }
    }

    /** Writes the legacy definition with the box's string and int params. */
    protected void serialize(ServerMessage message, Room room, String stringParam, int[] intParams) {
        this.links.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getUserItem(item.getId()) == null);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.links.size());
        for (UserItem item : this.links) message.appendInt(BuildersClubItemIds.toWire(item.getId()));
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
