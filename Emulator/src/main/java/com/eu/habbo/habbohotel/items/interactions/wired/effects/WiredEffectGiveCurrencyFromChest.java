package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
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
 */
public class WiredEffectGiveCurrencyFromChest extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_CURRENCY_FROM_CHEST;

    private final List<Integer> chestIds = new ArrayList<>();
    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveCurrencyFromChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveCurrencyFromChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || this.amount <= 0) return;

        InteractionWiredChest chest = this.resolveChest(room);
        if (chest == null) return;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null) continue;

            ChestStorage contents = chest.getContents();
            // Take from the first currency entry that still has stock.
            for (ChestStorage.Entry entry : new ArrayList<>(contents.entries())) {
                if (entry.kind != ChestStorage.KIND_CURRENCY) continue;

                int given = contents.take(ChestStorage.KIND_CURRENCY, entry.type, this.amount);
                if (given > 0) {
                    grant(habbo, entry.type, given);
                    chest.persistContents();
                }
                break;
            }
        }
    }

    private InteractionWiredChest resolveChest(Room room) {
        for (Integer id : this.chestIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest chest) {
                return chest;
            }
        }
        return null;
    }

    private static void grant(Habbo habbo, int currencyType, int amount) {
        if (currencyType < 0) {
            habbo.giveCredits(amount);
        } else {
            habbo.givePoints(currencyType, amount);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 2) throw new WiredSaveException("invalid data");

        this.amount = Math.max(0, params[0]);
        this.userSource = params[1];

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
        return WiredManager.getGson().toJson(new JsonData(this.amount, this.userSource, this.getDelay(), this.chestIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.amount = Math.max(0, data.amount);
        this.userSource = data.userSource;
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
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.amount);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.chestIds.clear();
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
        int userSource;
        int delay;
        List<Integer> chestIds;

        public JsonData(int amount, int userSource, int delay, List<Integer> chestIds) {
            this.amount = amount;
            this.userSource = userSource;
            this.delay = delay;
            this.chestIds = chestIds;
        }
    }
}
