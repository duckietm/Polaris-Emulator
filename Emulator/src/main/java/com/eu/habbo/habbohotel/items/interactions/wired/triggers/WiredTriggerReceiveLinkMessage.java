package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Official receive link message (29, {@code wf_trg_receive_message}): fires when one of the picked WIRED
 * Links receives a link message; the message is the stack's {@code $(trigger_text)}. No settings.
 */
public class WiredTriggerReceiveLinkMessage extends WiredTriggerReceiveLinkBase {
    public static final WiredTriggerType type = WiredTriggerType.RECEIVE_LINK_MESSAGE;

    public WiredTriggerReceiveLinkMessage(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerReceiveLinkMessage(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredLinkPayload.Kind linkKind() {
        return WiredLinkPayload.Kind.MESSAGE;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event.getType() == WiredEvent.Type.LINK_MESSAGE_RECEIVED && this.matchesLink(event);
    }

    @Override
    public WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops) {
        return this.receiveEvent(room, link, payload, hops, 0);
    }

    @Override
    public WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops, int sourceRoomId) {
        return WiredEvent.builder(WiredEvent.Type.LINK_MESSAGE_RECEIVED, room)
                .sourceItem(link)
                .tile(room.getLayout() != null ? room.getLayout().getTile(link.getX(), link.getY()) : null)
                .text(payload.message())
                .linkHops(hops)
                .linkSourceRoomId(sourceRoomId)
                .triggeredByEffect(true)
                .build();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serialize(message, room, "", new int[0]);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.linkIds()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        JsonData data = null;
        if (wiredData != null && wiredData.startsWith("{")) {
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException e) {
                data = null;
            }
        }
        this.loadLinks(data != null ? data.itemIds : null, room);
    }

    static class JsonData {
        List<Integer> itemIds;

        JsonData(List<Integer> itemIds) {
            this.itemIds = itemIds;
        }
    }
}
