package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreDataEntry;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * "User has X points on the scoreboard" ({@code wf_cnd_x_points_leaderboard}): the best score the
 * resolved users hold on the selected highscore boards, compared to a number. Three ints: points,
 * comparison ({@link WiredComparison} codes), quantifier; the users in the official source slot, plus
 * the boards. A user with
 * no entry has zero points; a box with no board selected never passes. The negative box is
 * {@link WiredConditionNotHabboHasHighscorePoints}.
 */
public class WiredConditionHabboHasHighscorePoints extends InteractionWiredCondition
        implements WiredOfficialQuantifier {
    public static final WiredConditionType type = WiredConditionType.USER_HIGHSCORE_POINTS;
    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;

    private int points = 0;
    private int comparison = WiredComparison.GREATER_EQUAL;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;
    private final List<Integer> boardIds = new ArrayList<>();
    private IntFunction<List<WiredHighscoreDataEntry>> scores = boardId -> WiredPlatform.gameEnvironment()
            .getItemManager()
            .getHighscoreManager()
            .getEntriesForItemId(boardId);

    public WiredConditionHabboHasHighscorePoints(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboHasHighscorePoints(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    void scores(IntFunction<List<WiredHighscoreDataEntry>> scores) {
        this.scores = scores;
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
        if (room == null || this.boardIds.isEmpty()) return false;

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier == QUANTIFIER_ALL);

        boolean anyMatched = false;
        for (RoomUnit target : targets) {
            Habbo habbo = target == null ? null : room.getHabbo(target);
            if (habbo == null || habbo.getHabboInfo() == null) {
                if (this.quantifier == QUANTIFIER_ALL) return false;
                continue;
            }
            boolean matched =
                    this.matchesTarget(this.bestScore(habbo.getHabboInfo().getId()));
            if (this.quantifier == QUANTIFIER_ALL) {
                if (!matched) return false;
                anyMatched = true;
            } else if (matched) {
                return true;
            }
        }
        return this.quantifier == QUANTIFIER_ALL && anyMatched;
    }

    /** Whether one user's best score satisfies the box; the negative box turns this around. */
    protected boolean matchesTarget(int best) {
        return WiredComparison.compare(best, this.points, this.comparison);
    }

    private int bestScore(int userId) {
        int best = 0;
        for (Integer boardId : this.boardIds) {
            List<WiredHighscoreDataEntry> entries = this.scores.apply(boardId);
            if (entries == null) continue;
            for (WiredHighscoreDataEntry entry : entries) {
                if (entry != null
                        && entry.getUserIds() != null
                        && entry.getUserIds().contains(userId)) {
                    best = Math.max(best, entry.getScore());
                }
            }
        }
        return best;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.points = Math.max(0, param(params, 0, 0));
        this.comparison = WiredComparison.normalize(param(params, 1, WiredComparison.GREATER_EQUAL));
        this.userSource = settings.getUserSourceTypes().length > 0
                ? settings.getUserSourceTypes()[0]
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = settings.getQuantifierCode() == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.boardIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.boardIds.add(id);
        }
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.points,
                        this.comparison,
                        this.userSource,
                        this.quantifier,
                        new ArrayList<>(this.boardIds)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.boardIds.size());
        for (Integer id : this.boardIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.points);
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

        this.points = Math.max(0, data.points);
        this.comparison = WiredComparison.normalize(data.comparison);
        this.userSource = data.userSource;
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        if (data.boardIds != null) this.boardIds.addAll(data.boardIds);
    }

    @Override
    public void onPickUp() {
        this.points = 0;
        this.comparison = WiredComparison.GREATER_EQUAL;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.boardIds.clear();
        this.setExtradata("");
    }

    private static int param(int[] params, int index, int fallback) {
        return (params != null && params.length > index) ? params[index] : fallback;
    }

    static class JsonData {
        int points;
        int comparison;
        int userSource;
        int quantifier;
        List<Integer> boardIds;

        JsonData(int points, int comparison, int userSource, int quantifier, List<Integer> boardIds) {
            this.points = points;
            this.comparison = comparison;
            this.userSource = userSource;
            this.quantifier = quantifier;
            this.boardIds = boardIds;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
