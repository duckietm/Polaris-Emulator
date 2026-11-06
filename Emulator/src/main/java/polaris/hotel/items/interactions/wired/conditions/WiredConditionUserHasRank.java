package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredComparison;
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
 * "User has rank" ({@code wf_cnd_habbo_has_rank}): the account rank of the resolved users compared
 * to a rank the builder types. Three ints: rank, comparison ({@link WiredComparison} codes),
 * quantifier (0 all, 1 any); the users in the official source slot. The negative box is
 * {@link WiredConditionNotUserHasRank}.
 */
public class WiredConditionUserHasRank extends InteractionWiredCondition implements WiredOfficialQuantifier {
    public static final WiredConditionType type = WiredConditionType.USER_RANK;
    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;
    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 1_000;

    private int rank = MIN_RANK;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionUserHasRank(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserHasRank(
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
        Room room = ctx == null ? null : ctx.room();
        if (room == null) return false;

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        // "All" holds over no users and "any" does not. The negative box turns the comparison around
        // per user ("all lack the rank"), so it reads an empty selection the same way.
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier == QUANTIFIER_ALL);

        boolean anyMatched = false;
        for (RoomUnit target : targets) {
            User user = target == null ? null : room.getUser(target);
            Integer reached = rankOf(user);
            boolean matched = reached != null && this.matchesTarget(reached);
            if (this.quantifier == QUANTIFIER_ALL) {
                if (!matched) return false;
                anyMatched = true;
            } else if (matched) {
                return true;
            }
        }
        return this.quantifier == QUANTIFIER_ALL && anyMatched;
    }

    /** Whether one user's rank satisfies the box; the negative box turns this around. */
    protected boolean matchesTarget(int reached) {
        return WiredComparison.compare(reached, this.rank, this.comparison);
    }

    private static Integer rankOf(User user) {
        if (user == null || user.getUserInfo() == null || user.getUserInfo().getRank() == null) return null;
        return user.getUserInfo().getRank().getId();
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.rank = normalizeRank(param(params, 0, MIN_RANK));
        this.comparison = WiredComparison.normalize(param(params, 1, WiredComparison.GREATER_EQUAL));
        this.userSource = settings.getUserSourceTypes().length > 0
                ? settings.getUserSourceTypes()[0]
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = settings.getQuantifierCode() == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.rank, this.comparison, this.userSource, this.quantifier));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.rank);
        message.appendInt(this.comparison);
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
        if (data == null) return;

        this.rank = normalizeRank(data.rank);
        this.comparison = WiredComparison.normalize(data.comparison);
        this.userSource = data.userSource;
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    @Override
    public void onPickUp() {
        this.rank = MIN_RANK;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.setExtradata("");
    }

    static int normalizeRank(int value) {
        return Math.max(MIN_RANK, Math.min(MAX_RANK, value));
    }

    private static int param(int[] params, int index, int fallback) {
        return (params != null && params.length > index) ? params[index] : fallback;
    }

    static class JsonData {
        int rank;
        int comparison;
        int userSource;
        int quantifier;

        JsonData(int rank, int comparison, int userSource, int quantifier) {
            this.rank = rank;
            this.comparison = comparison;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
