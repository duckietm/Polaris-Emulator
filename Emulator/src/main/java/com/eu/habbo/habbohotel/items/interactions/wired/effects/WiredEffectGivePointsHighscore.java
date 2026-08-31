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
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

        for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
            Emulator.getGameEnvironment()
                    .getItemManager()
                    .getHighscoreManager()
                    .addHighscoreData(new WiredHighscoreDataEntry(item.getId(), userIds, this.amount, true, now));

            ((InteractionWiredHighscore) item).reloadData();
            room.updateItem(item);
        }
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
