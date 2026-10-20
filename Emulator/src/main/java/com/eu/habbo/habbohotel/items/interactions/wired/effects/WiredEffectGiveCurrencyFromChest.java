package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestAirWire;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestWithdrawals;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Give Currency From Chest (furni classname {@code wf_act_give_currency}). Dispenses up to
 * {@code amount} currency from a selected {@link InteractionWiredChest} to the resolved user(s),
 * decrementing and persisting the chest pool. If the chest is empty nothing is given. Safe: it only
 * grants what the chest actually holds (no minting beyond the configured pool).
 *
 * <p>Official layout (45): the shared {@link WiredChestReward} ints with the earnings category (11
 * games, 13 agency) as the sixth; furni slot 0 the chests, user slot 0 the rewarded users. The coins go to that
 * row of the earnings window when the hotel runs it ({@link com.eu.habbo.habbohotel.earnings.WiredEarnings}).
 */
public class WiredEffectGiveCurrencyFromChest extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialVariableIds {
    static final int EARNINGS_GAMES = 11;
    static final int EARNINGS_AGENCY = 13;

    public static final WiredEffectType type = WiredEffectType.GIVE_CURRENCY_FROM_CHEST;

    private final List<Integer> chestIds = new ArrayList<>();
    private final WiredChestReward reward = new WiredChestReward();
    private int earningsCategory = EARNINGS_GAMES;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int chestSource = WiredChestTargets.DEFAULT_CHEST_SOURCE;
    private int secondFurniSource = WiredChestTargets.DEFAULT_SECOND_FURNI_SOURCE;
    private int secondUserSource = WiredChestTargets.DEFAULT_SECOND_USER_SOURCE;

    public WiredEffectGiveCurrencyFromChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveCurrencyFromChest(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;
        Integer resolved = this.reward.resolveAmount(ctx, room, 1);
        if (resolved == null || resolved <= 0) return;
        boolean everything = this.reward.mode == WiredChestReward.MODE_EVERYTHING;

        // Only upgraded, unlocked chests are used; with none the triggering user is told why.
        List<InteractionWiredChestCurrency> chests = WiredChestTargets.resolveAll(
                ctx, room, this.chestSource, this.chestIds, InteractionWiredChestCurrency.class);
        if (chests.isEmpty()) {
            WiredChestTargets.answerNoUsableChest(ctx, room);
            return;
        }

        List<RoomUnit> units = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        // AIR: "everything" cannot be split between several users (fail 16).
        if (everything && units.size() > 1) {
            WiredChestTargets.answerCannotGiveAllToMany(ctx, room, "give_currency_from_chest", this.getId());
            return;
        }

        for (RoomUnit unit : units) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null) continue;

            // Taken from the chests in order until the amount is reached or they are empty.
            int remaining = everything ? Integer.MAX_VALUE : resolved;
            int total = 0;
            for (InteractionWiredChestCurrency chest : chests) {
                if (remaining <= 0) break;
                // The coins land in the earnings window row the box names (AIR earnings category).
                int taken = ChestWithdrawals.coins(
                                habbo,
                                chest,
                                room,
                                everything ? -1 : remaining,
                                ChestTransactionLog.SOURCE_WIRED,
                                this.earningsCategory)
                        .coins();
                total += taken;
                remaining -= taken;
            }
            if (total > 0) this.reward.announce(habbo, ChestAirWire.givenRule(total, List.of()));
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Refused before anything changes: the picks must all be upgraded chests of this kind.
        WiredChestTargets.validatePicks(
                this.getRoom(),
                settings.getFurniIds(),
                InteractionWiredChestCurrency.class,
                WiredChestTargets.REQUIRE_COIN_CHESTS);

        this.earningsCategory = normalizeCategory(this.reward.read(settings, 1, EARNINGS_GAMES));
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.chestSource =
                WiredChestTargets.normalizeChestSource(settings.furniSource(0, WiredChestTargets.DEFAULT_CHEST_SOURCE));
        this.secondFurniSource = settings.furniSource(1, WiredChestTargets.DEFAULT_SECOND_FURNI_SOURCE);
        this.secondUserSource = settings.userSource(1, WiredChestTargets.DEFAULT_SECOND_USER_SOURCE);

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                this.chestIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(this.reward.amount, this.userSource, this.getDelay(), this.chestIds);
        this.reward.writeTo(data);
        data.furniSource = this.chestSource;
        data.furniSource2 = this.secondFurniSource;
        data.userSource2 = this.secondUserSource;
        data.earningsCategory = this.earningsCategory;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        // A box saved before the official layout gives its amount; 0 gave nothing and still does.
        this.reward.amount = Math.max(0, data.amount);
        this.reward.readFrom(data);
        this.earningsCategory = normalizeCategory(data.earningsCategory);
        this.userSource = data.userSource;
        // A row saved before the sources were stored always used its picked chests.
        if (data.furniSource != null) this.chestSource = WiredChestTargets.normalizeChestSource(data.furniSource);
        if (data.furniSource2 != null) this.secondFurniSource = data.furniSource2;
        if (data.userSource2 != null) this.secondUserSource = data.userSource2;
        this.setDelay(data.delay);
        if (data.chestIds != null) {
            this.chestIds.addAll(data.chestIds);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.reward.popupText);
        int[] params = this.reward.officialParams(this.earningsCategory);
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.chestIds.clear();
        this.reward.reset(1);
        this.earningsCategory = EARNINGS_GAMES;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.chestSource = WiredChestTargets.DEFAULT_CHEST_SOURCE;
        this.secondFurniSource = WiredChestTargets.DEFAULT_SECOND_FURNI_SOURCE;
        this.secondUserSource = WiredChestTargets.DEFAULT_SECOND_USER_SOURCE;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.chestSource, this.secondFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.secondUserSource};
    }

    @Override
    public String[] officialVariableIds() {
        return this.reward.officialVariableIds();
    }

    private static int normalizeCategory(int category) {
        return category == EARNINGS_AGENCY ? EARNINGS_AGENCY : EARNINGS_GAMES;
    }

    static class JsonData extends WiredChestReward.Stored {
        int earningsCategory;
        int amount;
        int userSource;
        int delay;
        List<Integer> chestIds;
        Integer furniSource;
        Integer furniSource2;
        Integer userSource2;

        public JsonData(int amount, int userSource, int delay, List<Integer> chestIds) {
            this.amount = amount;
            this.userSource = userSource;
            this.delay = delay;
            this.chestIds = chestIds;
        }
    }
}
