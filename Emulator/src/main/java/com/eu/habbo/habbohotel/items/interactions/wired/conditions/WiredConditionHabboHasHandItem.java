package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionHabboHasHandItem extends InteractionWiredCondition {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int MAX_HAND_ITEM_ID = 10_000;

    public static final WiredConditionType type = WiredConditionType.ACTOR_HAS_HANDITEM;

    private int handItem;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionHabboHasHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboHasHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
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
        message.appendInt(3);
        message.appendInt(this.handItem);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        this.handItem = WiredUserConditionInputGuard.normalizeHandItemId(settings.getIntParams()[0]);
        int[] params = settings.getIntParams();
        this.userSource = (params.length > 1) ? WiredUserConditionInputGuard.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.matchesAnyTarget(targets);
        }

        return this.matchesAllTargets(targets);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.handItem,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            this.onPickUp();
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
                this.onPickUp();
                return;
            }
            this.handItem = WiredUserConditionInputGuard.normalizeHandItemId(data.handItemId);
            this.userSource = WiredUserConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
        } else {
            try {
                this.handItem = WiredUserConditionInputGuard.normalizeHandItemId(Integer.parseInt(wiredData));
            } catch (NumberFormatException ignored) {
                this.handItem = 0;
            }
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ALL;
        }
    }

    @Override
    public void onPickUp() {
        this.handItem = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    protected int getHandItem() {
        return this.handItem;
    }

    protected int getUserSource() {
        return this.userSource;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected boolean matchesAnyTarget(List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (roomUnit != null && roomUnit.getHandItem() == this.handItem) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesAllTargets(List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (roomUnit == null || roomUnit.getHandItem() != this.handItem) {
                return false;
            }
        }

        return true;
    }

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int handItemId;
        int userSource;
        int quantifier;

        public JsonData(int handItemId, int userSource, int quantifier) {
            this.handItemId = handItemId;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
