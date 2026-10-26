package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Official Link Speaker add-on (2003, {@code wf_xtra_link_speaker}, AIR {@code §_-V2H§}): its picked WIRED Links
 * become endpoints of the send link boxes in its stack. Int params {@code [message, dataframe]} choose which kind
 * of send it speaks for ({@code wiredfurni.params.link_speaker.speak_for}).
 */
public class WiredExtraLinkSpeaker extends InteractionWiredExtra {
    public static final int CODE = 2003;

    private final Set<HabboItem> links = new LinkedHashSet<>();
    private boolean message = true;
    private boolean dataframe = true;

    public WiredExtraLinkSpeaker(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraLinkSpeaker(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean speaksFor(WiredLinkPayload.Kind kind) {
        return kind == WiredLinkPayload.Kind.MESSAGE ? this.message : this.dataframe;
    }

    public List<HabboItem> getLinks() {
        return new ArrayList<>(this.links);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        Room room = settings.getRoom();
        if (room == null && WiredPlatform.gameEnvironment() != null) {
            room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        }
        InteractionWiredLink.refuseNonLinkPicks(room, settings.getFurniIds());
        this.links.clear();
        if (room != null) {
            for (int id : settings.getFurniIds()) {
                if (this.links.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
                HabboItem item = room.getHabboItem(id);
                if (InteractionWiredLink.isLink(item)) this.links.add(item);
            }
        }
        this.message = params.length > 0 && params[0] == 1;
        this.dataframe = params.length > 1 && params[1] == 1;
        return true;
    }

    @Override
    public String getWiredData() {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : this.links) ids.add(item.getId());
        return WiredManager.getGson().toJson(new JsonData(ids, this.message, this.dataframe));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.links.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.links.size());
        for (HabboItem item : this.links) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.message ? 1 : 0);
        message.appendInt(this.dataframe ? 1 : 0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;
        this.message = data.message;
        this.dataframe = data.dataframe;
        if (data.itemIds != null && room != null) {
            for (Integer id : data.itemIds) {
                if (id == null) continue;
                HabboItem item = room.getHabboItem(id);
                if (InteractionWiredLink.isLink(item)) this.links.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.links.clear();
        this.message = true;
        this.dataframe = true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    static class JsonData {
        List<Integer> itemIds;
        boolean message;
        boolean dataframe;

        JsonData(List<Integer> itemIds, boolean message, boolean dataframe) {
            this.itemIds = itemIds;
            this.message = message;
            this.dataframe = dataframe;
        }
    }
}
