package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionGroupMember extends InteractionWiredCondition {
    private static final int GROUP_CURRENT_ROOM = 0;
    private static final int GROUP_SELECTED = 1;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.ACTOR_IN_GROUP;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int groupType = GROUP_CURRENT_ROOM;
    private int selectedGroupId = 0;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionGroupMember(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionGroupMember(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        int targetGroupId = this.resolveTargetGroupId(room);
        if (targetGroupId == 0)
            return false;

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            for (RoomUnit roomUnit : targets) {
                Habbo habbo = room.getHabbo(roomUnit);
                if (habbo != null && habbo.getHabboStats().hasGuild(targetGroupId)) {
                    return true;
                }
            }

            return false;
        }

        for (RoomUnit roomUnit : targets) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo == null || !habbo.getHabboStats().hasGuild(targetGroupId)) {
                return false;
            }
        }

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.userSource,
                this.groupType,
                this.selectedGroupId,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                if (data == null) {
                    return;
                }

                this.userSource = this.normalizeUserSource(data.userSource);
                this.groupType = this.normalizeGroupType(data.groupType);
                this.selectedGroupId = this.normalizeSelectedGroupId(data.selectedGroupId);
                this.quantifier = this.normalizeQuantifier(data.quantifier);
                return;
            }
            this.userSource = this.normalizeUserSource(Integer.parseInt(wiredData));
        } catch (Exception ignored) {
            this.resetSettings();
        }
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.userSource);
        message.appendInt(this.groupType);
        message.appendInt(this.selectedGroupId);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.userSource = (params.length > 0) ? this.normalizeUserSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.groupType = (params.length > 1) ? this.normalizeGroupType(params[1]) : GROUP_CURRENT_ROOM;
        this.selectedGroupId = (params.length > 2) ? this.normalizeSelectedGroupId(params[2]) : 0;
        this.quantifier = (params.length > 3) ? this.normalizeQuantifier(params[3]) : QUANTIFIER_ALL;
        return true;
    }

    private void resetSettings() {
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.groupType = GROUP_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.quantifier = QUANTIFIER_ALL;
    }

    private int resolveTargetGroupId(Room room) {
        if (room == null) {
            return 0;
        }

        if (this.groupType == GROUP_SELECTED) {
            return this.selectedGroupId;
        }

        return room.getGuildId();
    }

    private int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeGroupType(int value) {
        return (value == GROUP_SELECTED) ? GROUP_SELECTED : GROUP_CURRENT_ROOM;
    }

    private int normalizeSelectedGroupId(int value) {
        return Math.max(0, value);
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    static class JsonData {
        int userSource;
        int groupType;
        int selectedGroupId;
        int quantifier;

        public JsonData(int userSource, int groupType, int selectedGroupId, int quantifier) {
            this.userSource = userSource;
            this.groupType = groupType;
            this.selectedGroupId = selectedGroupId;
            this.quantifier = quantifier;
        }
    }
}
