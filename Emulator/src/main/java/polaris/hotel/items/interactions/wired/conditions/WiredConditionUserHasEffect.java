package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionUserHasEffect extends InteractionWiredCondition implements WiredOfficialQuantifier {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int MAX_EFFECT_ID = 10_000;

    public static final WiredConditionType type = WiredConditionType.ACTOR_WEARS_EFFECT;

    protected int effectId = 0;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ANY;

    public WiredConditionUserHasEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserHasEffect(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier == QUANTIFIER_ALL);

        if (this.quantifier == QUANTIFIER_ALL) {
            return this.matchesAllTargets(targets);
        }

        return this.matchesAnyTarget(targets);
    }

    protected boolean matchesAllTargets(List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (!this.matchesEffect(roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesAnyTarget(List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesEffect(roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesEffect(RoomUnit roomUnit) {
        // Effect 0 is "any effect", as officially.
        return roomUnit != null
                && (this.effectId == 0 ? roomUnit.getEffectId() != 0 : roomUnit.getEffectId() == this.effectId);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.effectId, this.userSource, this.quantifier));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
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
            this.effectId = WiredUserConditionInputGuard.normalizeEffectId(data.effectId);
            this.userSource = WiredUserConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier, QUANTIFIER_ANY);
        } else {
            try {
                this.effectId = WiredUserConditionInputGuard.normalizeEffectId(Integer.parseInt(wiredData));
            } catch (NumberFormatException ignored) {
                this.effectId = 0;
            }
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ANY;
        }
    }

    @Override
    public void onPickUp() {
        this.effectId = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ANY;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.effectId);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;
        int[] params = settings.getIntParams();
        this.effectId = WiredUserConditionInputGuard.normalizeEffectId(params[0]);
        // Official layout: [effect (0 any)], the users in the official source slot, the quantifier in its slot.
        this.userSource = settings.getUserSourceTypes().length > 0
                ? WiredUserConditionInputGuard.normalizeUserSource(settings.getUserSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode(), QUANTIFIER_ALL);

        return true;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }

        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected int normalizeEffectId(int value) {
        return Math.max(0, Math.min(MAX_EFFECT_ID, value));
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int effectId;
        int userSource;
        Integer quantifier;

        public JsonData(int effectId, int userSource, int quantifier) {
            this.effectId = effectId;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.getQuantifier();
    }
}
