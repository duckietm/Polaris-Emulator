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
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

/**
 * Passes when the resolved user's motto contains the configured text (case-insensitive). Reuses the
 * ACTOR_WEARS_BADGE dialog (a single text field + source + quantifier — the text holds the motto
 * substring instead of a badge code), so no new client dialog is required. An empty configured text
 * never matches.
 */
public class WiredConditionMottoContains extends InteractionWiredCondition implements WiredOfficialQuantifier {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int MAX_TEXT_LENGTH = 64;

    public static final WiredConditionType type = WiredConditionType.USER_MOTTO;

    protected String text = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ANY;

    public WiredConditionMottoContains(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionMottoContains(
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
            if (!this.matchesMotto(room, roomUnit)) {
                return false;
            }
        }

        return true;
    }

    protected boolean matchesAnyTarget(Room room, List<RoomUnit> targets) {
        for (RoomUnit roomUnit : targets) {
            if (this.matchesMotto(room, roomUnit)) {
                return true;
            }
        }

        return false;
    }

    protected boolean matchesMotto(Room room, RoomUnit roomUnit) {
        if (this.text.isEmpty()) {
            return false;
        }

        User user = room.getUser(roomUnit);
        if (user == null || user.getUserInfo() == null) {
            return false;
        }

        String motto = user.getUserInfo().getMotto();
        return motto != null && motto.toLowerCase().contains(this.text.toLowerCase());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.text, this.userSource, this.quantifier));
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
            this.text = this.normalizeText(data.text);
            this.userSource = this.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
        } else {
            this.text = this.normalizeText(wiredData);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ANY;
        }
    }

    @Override
    public void onPickUp() {
        this.text = "";
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
        // As the badge box: the text in the string, no int params, the users in the official source slot and
        // the quantifier in its slot.
        message.appendString(this.text);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.text = this.normalizeText(settings.getStringParam());
        this.userSource = settings.getUserSourceTypes().length > 0
                ? this.normalizeUserSource(settings.getUserSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());

        return true;
    }

    protected int normalizeQuantifier(Integer value) {
        if (value == null) {
            return QUANTIFIER_ANY;
        }

        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH);
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String text;
        int userSource;
        Integer quantifier;

        public JsonData(String text, int userSource, int quantifier) {
            this.text = text;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
