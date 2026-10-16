package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestNotifications;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestWiredFurniUtil;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
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
 * Give Furni From Chest (furni classname {@code wf_act_give_furni}). Dispenses up to {@code amount}
 * copies of the selected {@link InteractionWiredChest}'s stored furni type to the resolved user(s),
 * decrementing + persisting the chest. Never gives more than the chest holds.
 *
 * <p>Official layout (46): the shared {@link WiredChestReward} ints with the iteration order (0 random,
 * 1 oldest first, 2 newest first) as the sixth; furni slot 0 the chests, user slot 0 the rewarded
 * users. A box saved before the official layout keeps taking the oldest furni first.
 */
public class WiredEffectGiveFurniFromChest extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialVariableIds {
    static final int ITERATION_RANDOM = 0;
    static final int ITERATION_OLDEST = 1;
    static final int ITERATION_NEWEST = 2;
    private static final java.util.Random RANDOM = new java.util.Random();

    public static final WiredEffectType type = WiredEffectType.GIVE_FURNI_FROM_CHEST;

    private final List<Integer> chestIds = new ArrayList<>();
    private final WiredChestReward reward = new WiredChestReward();
    private int iterationType = ITERATION_RANDOM;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int chestSource = WiredChestTargets.DEFAULT_CHEST_SOURCE;
    private int secondFurniSource = WiredChestTargets.DEFAULT_SECOND_FURNI_SOURCE;
    private int secondUserSource = WiredChestTargets.DEFAULT_SECOND_USER_SOURCE;

    public WiredEffectGiveFurniFromChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveFurniFromChest(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;
        Integer resolved = this.reward.resolveAmount(ctx, room, 1);
        if (resolved == null || resolved <= 0) return;
        int perUser = resolved;

        // Only an upgraded, unlocked chest is used; with none the triggering user is told why.
        InteractionWiredChest chest =
                WiredChestTargets.resolve(ctx, room, this.chestSource, this.chestIds, InteractionWiredChestFurni.class);
        if (chest == null) {
            WiredChestTargets.answerNoUsableChest(ctx, room);
            return;
        }

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getClient() == null) continue;

            // The stored rows themselves leave the chest (with their state), in the box's order.
            List<ChestFurniStoredItem> removed =
                    chest.getContents().removeFurniInOrder(this.iterationType, perUser, RANDOM);
            if (removed.isEmpty()) break;

            int given = ChestWiredFurniUtil.giveStoredItemsToInventory(habbo, removed);
            ChestTransactionLog.record(
                    room.getId(),
                    chest.getId(),
                    ChestStorage.KIND_FURNI,
                    ChestTransactionLog.TYPE_WITHDRAW,
                    ChestTransactionLog.SOURCE_WIRED,
                    habbo,
                    -1,
                    given,
                    0,
                    removed);
            chest.persistContents(room);
            ChestNotifications.wired(chest, room, given);
            this.reward.showPopup(habbo, given);
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
                InteractionWiredChestFurni.class,
                WiredChestTargets.REQUIRE_FURNI_CHESTS);

        this.iterationType = normalizeIteration(this.reward.read(settings, 1, ITERATION_RANDOM));
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
        data.iterationType = this.iterationType;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.reward.amount = Math.max(1, data.amount);
        this.reward.readFrom(data);
        // A row saved before the official layout took the oldest furni first.
        this.iterationType = normalizeIteration(data.iterationType == null ? ITERATION_OLDEST : data.iterationType);
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
        int[] params = this.reward.officialParams(this.iterationType);
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
        this.iterationType = ITERATION_RANDOM;
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

    private static int normalizeIteration(int iteration) {
        return (iteration == ITERATION_OLDEST || iteration == ITERATION_NEWEST) ? iteration : ITERATION_RANDOM;
    }

    static class JsonData extends WiredChestReward.Stored {
        Integer iterationType;
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
