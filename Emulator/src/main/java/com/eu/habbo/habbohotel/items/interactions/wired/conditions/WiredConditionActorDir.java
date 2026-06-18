package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionActorDir extends InteractionWiredCondition {
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;
    private static final int ALL_DIRECTIONS_MASK = createDirectionMask();

    public static final WiredConditionType type = WiredConditionType.ACTOR_DIR;

    private int directionMask = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionActorDir(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionActorDir(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(this.directionMask);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.directionMask = (params.length > 0) ? this.normalizeDirectionMask(params[0]) : 0;
        this.userSource = (params.length > 1) ? this.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (this.directionMask == 0) {
            return false;
        }

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) {
            return false;
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return targets.stream().anyMatch(this::matchesDirection);
        }

        return targets.stream().allMatch(this::matchesDirection);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.directionMask,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException exception) {
                this.onPickUp();
                return;
            }

            if (data == null) {
                return;
            }

            this.directionMask = this.normalizeDirectionMask(data.directionMask);
            this.userSource = this.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
            return;
        }

        String[] parts = wiredData.split("\t");

        try {
            if (parts.length > 0) {
                this.directionMask = this.normalizeDirectionMask(Integer.parseInt(parts[0]));
            }
            if (parts.length > 1) {
                this.userSource = this.normalizeUserSource(Integer.parseInt(parts[1]));
            }
            if (parts.length > 2) {
                this.quantifier = this.normalizeQuantifier(Integer.parseInt(parts[2]));
            }
        } catch (NumberFormatException ignored) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.directionMask = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    private boolean matchesDirection(RoomUnit roomUnit) {
        if (roomUnit == null || roomUnit.getBodyRotation() == null) {
            return false;
        }

        int direction = roomUnit.getBodyRotation().getValue();

        return (this.directionMask & (1 << direction)) != 0;
    }

    int normalizeDirectionMask(int value) {
        return value & ALL_DIRECTIONS_MASK;
    }

    int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    private static int createDirectionMask() {
        int mask = 0;

        for (int direction = 0; direction < 8; direction++) {
            mask |= (1 << direction);
        }

        return mask;
    }

    static class JsonData {
        int directionMask;
        int userSource;
        int quantifier;

        public JsonData(int directionMask, int userSource, int quantifier) {
            this.directionMask = directionMask;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
