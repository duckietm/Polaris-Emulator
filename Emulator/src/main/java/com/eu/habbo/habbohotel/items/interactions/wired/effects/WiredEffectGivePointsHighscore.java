package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNumericInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreDataEntry;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Records a score for the resolved users on every highscore board in the room.
 *
 * <p>Until now a board could only be written by {@link com.eu.habbo.habbohotel.games.Game#onEnd()},
 * which is reached through a game timer. A room that scored with wired alone - no teams, no timer -
 * had no way to put anything on a board at all, and said nothing about why.
 *
 * <p>Every entry counts as a win, so a "most wins" board treats one firing as one win. The other
 * three board types ignore the flag and read the amount.
 *
 * <p>Server-only: it reuses the {@link WiredEffectType#EFFECT_AMOUNT} dialog - one number plus a
 * user-source selector - so it needs no new client window. The amount is capped per firing by
 * {@link WiredNumericInputGuard#maxRewardAmount()}; a leaderboard position is not currency, so it is
 * not behind the reward permission.
 */
public class WiredEffectGivePointsHighscore extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.EFFECT_AMOUNT;

    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGivePointsHighscore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGivePointsHighscore(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.amount + "");
        message.appendInt(1);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int nextAmount = WiredNumericInputGuard.parsePositiveAmount(
                settings.getStringParam(), WiredNumericInputGuard.maxRewardAmount());
        if (nextAmount <= 0) {
            return false;
        }

        int[] params = settings.getIntParams();
        this.amount = nextAmount;
        this.userSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || this.amount <= 0 || room.getRoomSpecialTypes() == null) return;

        List<Integer> userIds = new ArrayList<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && habbo.getHabboInfo() != null) {
                userIds.add(habbo.getHabboInfo().getId());
            }
        }

        // No users resolved means no row: an entry with an empty user list would render as a blank
        // line on the board, which is worse than not appearing at all. resolveUsers has already
        // noted the empty source, so the firing still reports NO_TARGETS rather than going quiet.
        if (userIds.isEmpty()) {
            return;
        }

        int now = Emulator.getIntUnixTimestamp();

        WiredHighscoreManager highscores =
                Emulator.getGameEnvironment().getItemManager().getHighscoreManager();

        for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
            // A box called "give points" adds to what the user has, it does not file a fresh result
            // every time it fires. Appending left a classic board showing the same person five times
            // with the same ten points instead of once with fifty, and no way to reach a total at
            // all. The official give-score box accumulates too - it just does it inside the game and
            // writes once when the game ends, which is the path this box exists to work without.
            List<WiredHighscoreDataEntry> existing = highscores.getEntriesForItemId(item.getId());
            List<WiredHighscoreDataEntry> entries = (existing == null) ? new ArrayList<>() : new ArrayList<>(existing);
            int index = indexOfSameUsers(entries, userIds);

            if (index < 0) {
                highscores.addHighscoreData(new WiredHighscoreDataEntry(item.getId(), userIds, this.amount, true, now));
            } else {
                WiredHighscoreDataEntry previous = entries.get(index);
                entries.set(
                        index,
                        new WiredHighscoreDataEntry(
                                item.getId(), userIds, previous.getScore() + this.amount, true, now));
                highscores.setEntriesForItemId(item.getId(), entries);
            }

            ((InteractionWiredHighscore) item).reloadData();
            room.updateItem(item);
        }
    }

    /**
     * The row this exact set of users already holds on the board, or -1. Order does not matter: a
     * team scoring again is the same team however the resolver happened to list it.
     */
    private static int indexOfSameUsers(List<WiredHighscoreDataEntry> entries, List<Integer> userIds) {
        Set<Integer> wanted = new HashSet<>(userIds);

        for (int index = 0; index < entries.size(); index++) {
            WiredHighscoreDataEntry entry = entries.get(index);
            if (entry != null && new HashSet<>(entry.getUserIds()).equals(wanted)) {
                return index;
            }
        }

        return -1;
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.amount, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        // Through the shared guard: an unreadable row is no configuration, not a failed furni load.
        JsonData data = WiredEffectPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);

        if (data != null) {
            // The cap belongs to the box, not to the moment it was saved.
            this.amount = WiredNumericInputGuard.clampAmount(data.amount, WiredNumericInputGuard.maxRewardAmount());
            this.setDelay(WiredEffectPayloadGuard.delay(data.delay));
            this.userSource = data.userSource;
            return;
        }

        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void onPickUp() {
        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int amount;
        int delay;
        int userSource;

        public JsonData(int amount, int delay, int userSource) {
            this.amount = amount;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
