package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionUserWearsBadge extends InteractionWiredCondition implements WiredOfficialQuantifier {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int MAX_BADGE_CODE_LENGTH = 64;

    public static final WiredConditionType type = WiredConditionType.ACTOR_WEARS_BADGE;

    protected String badge = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ANY;

    public WiredConditionUserWearsBadge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserWearsBadge(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier == QUANTIFIER_ALL);

        if (this.quantifier == QUANTIFIER_ALL) {
            return this.matchesAllTargets(room, targets);
        }

        return this.matchesAnyTarget(room, targets);
    }

    protected boolean matchesAllTargets(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (!this.matchesBadge(room, roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesAnyTarget(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesBadge(room, roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        User user = room.getUser(roomUnit);
        if (user == null) {
            return false;
        }

        synchronized (user.getInventory().getBadgesComponent().getWearingBadges()) {
            for (UserBadge badge : user.getInventory().getBadgesComponent().getWearingBadges()) {
                if (badge.getCode().equalsIgnoreCase(this.badge)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.badge, this.userSource, this.quantifier));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null) {
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
            this.badge = WiredUserConditionInputGuard.normalizeBadgeCode(data.badge);
            this.userSource = WiredUserConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier, QUANTIFIER_ANY);
        } else {
            this.badge = WiredUserConditionInputGuard.normalizeBadgeCode(wiredData);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ANY;
        }
    }

    @Override
    public void onPickUp() {
        this.badge = "";
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
        message.appendString(this.badge);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.badge = WiredUserConditionInputGuard.normalizeBadgeCode(settings.getStringParam());
        // Official layout: the badge in the string, no int params, the users in the official source slot,
        // the quantifier (0 all, 1 any) in its slot.
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

    protected String normalizeBadge(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() <= MAX_BADGE_CODE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_BADGE_CODE_LENGTH);
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String badge;
        int userSource;
        Integer quantifier;

        public JsonData(String badge, int userSource, int quantifier) {
            this.badge = badge;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.getQuantifier();
    }
}
