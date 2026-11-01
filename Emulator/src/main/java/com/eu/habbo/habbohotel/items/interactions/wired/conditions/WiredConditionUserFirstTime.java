package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "First trigger" ({@code wf_cnd_first_trg}): the triggering user passes the first time and never
 * again. Who has passed is stored with the box, so a restart does not hand out a second first time;
 * picking the box up forgets everyone. No settings.
 */
public class WiredConditionUserFirstTime extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_ONCE;
    private static final int MAX_REMEMBERED_USERS = 10_000;

    private final Set<Integer> passedUserIds = new LinkedHashSet<>();

    public WiredConditionUserFirstTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserFirstTime(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Integer userId = WiredConditionUserCooldown.triggeringUserId(ctx);
        if (userId == null) return false;

        synchronized (this.passedUserIds) {
            return !this.passedUserIds.contains(userId);
        }
    }

    /** The first time is used up only when the stack runs, not when another condition stopped it. */
    @Override
    public void commit(WiredContext ctx) {
        Integer userId = WiredConditionUserCooldown.triggeringUserId(ctx);
        if (userId == null) return;

        synchronized (this.passedUserIds) {
            if (!this.passedUserIds.add(userId)) return;
            Iterator<Integer> oldest = this.passedUserIds.iterator();
            while (this.passedUserIds.size() > MAX_REMEMBERED_USERS && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        this.needsUpdate(true);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        synchronized (this.passedUserIds) {
            return WiredManager.getGson().toJson(new JsonData(new ArrayList<>(this.passedUserIds)));
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null || data.users == null) return;

        synchronized (this.passedUserIds) {
            for (Integer id : data.users) {
                if (id != null && id > 0) this.passedUserIds.add(id);
            }
        }
    }

    @Override
    public void onPickUp() {
        synchronized (this.passedUserIds) {
            this.passedUserIds.clear();
        }
        this.setExtradata("");
    }

    static class JsonData {
        List<Integer> users;

        JsonData(List<Integer> users) {
            this.users = users;
        }
    }
}
