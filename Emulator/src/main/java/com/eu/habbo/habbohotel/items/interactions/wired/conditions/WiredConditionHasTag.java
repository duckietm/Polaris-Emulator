package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when the resolved user has the configured profile tag (case-insensitive). Reuses the
 * ACTOR_WEARS_BADGE text dialog (text = tag), so no new client dialog is required. An empty configured
 * tag never matches. Reads {@link com.eu.habbo.habbohotel.users.HabboStats#hasTag}.
 */
public class WiredConditionHasTag extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int MAX_TAG_LENGTH = 38;

    public static final WiredConditionType type = WiredConditionType.ACTOR_WEARS_BADGE;

    protected String tag = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ANY;

    public WiredConditionHasTag(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHasTag(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return this.matchesAllTargets(room, targets);
        }

        return this.matchesAnyTarget(room, targets);
    }

    protected boolean matchesAllTargets(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (!this.matchesTag(room, roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesAnyTarget(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesTag(room, roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesTag(Room room, RoomUnit roomUnit) {
        if (this.tag.isEmpty()) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboStats() == null) {
            return false;
        }

        return habbo.getHabboStats().hasTag(this.tag);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.tag,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException ignored) {
                this.onPickUp();
                return;
            }
            if (data == null) {
                return;
            }
            this.tag = this.normalizeTag(data.tag);
            this.userSource = this.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
        } else {
            this.tag = this.normalizeTag(wiredData);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ANY;
        }
    }

    @Override
    public void onPickUp() {
        this.tag = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ANY;
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
        message.appendString(this.tag);
        message.appendInt(2);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.tag = this.normalizeTag(settings.getStringParam());
        int[] params = settings.getIntParams();
        this.userSource = (params.length > 0) ? this.normalizeUserSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 1) ? this.normalizeQuantifier(params[1]) : QUANTIFIER_ANY;

        return true;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(Integer value) {
        if (value == null) {
            return QUANTIFIER_ANY;
        }

        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected String normalizeTag(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() <= MAX_TAG_LENGTH ? normalized : normalized.substring(0, MAX_TAG_LENGTH);
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String tag;
        int userSource;
        Integer quantifier;

        public JsonData(String tag, int userSource, int quantifier) {
            this.tag = tag;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
