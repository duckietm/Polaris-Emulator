package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Initiate Transaction (furni classname {@code wf_act_init_transaction}). Selects one or more wired
 * CONTRACT furni ({@link InteractionWiredContract}) and executes their terms against the triggering
 * user as ONE atomic unit: it pre-checks EVERY term (user balance for PAY, chest stock for chest-sourced
 * RECEIVE) and only then mutates — so a transaction either fully happens or nothing changes. On success
 * it raises {@link WiredEvent.Type#TRANSACTION_COMPLETE} (the success branch); on any unsatisfiable
 * precondition it raises {@link WiredEvent.Type#TRANSACTION_FAIL} and commits nothing.
 *
 * <p>Atomicity is achieved synchronously on the room's single-threaded wired tick (compute all
 * preconditions first, mutate last). With NO contracts selected it degrades to the v1 signal model
 * (fires COMPLETE) for backward compatibility. Currency-only v1 (credits/points); the asset source/sink
 * is the existing config-based wired chest ({@link InteractionWiredChest}).</p>
 */
public class WiredEffectInitTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.INIT_TRANSACTION;

    private final List<Integer> contractIds = new ArrayList<>();

    public WiredEffectInitTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitTransaction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<InteractionWiredContract> contracts = new ArrayList<>();
        for (Integer id : this.contractIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredContract contract) contracts.add(contract);
        }

        // No contracts → v1 signal behaviour (fire COMPLETE unconditionally).
        if (contracts.isEmpty()) {
            this.fire(ctx, room, WiredEvent.Type.TRANSACTION_COMPLETE);
            return;
        }

        // Contracts require a triggering user to charge/reward.
        Habbo actor = ctx.actor().map(room::getHabbo).orElse(null);
        if (actor == null) {
            this.fire(ctx, room, WiredEvent.Type.TRANSACTION_FAIL);
            return;
        }

        // --- Aggregate (cumulative, so multiple terms of the same currency can't bypass the check) ---
        Map<Integer, Long> payTotal = new HashMap<>();                  // currencyType -> total to debit
        Map<Integer, InteractionWiredChest> chestById = new HashMap<>();
        Map<Integer, Map<Integer, Long>> chestTakeTotal = new HashMap<>(); // chestId -> (currencyType -> total to take)

        for (InteractionWiredContract contract : contracts) {
            InteractionWiredChest chest = this.resolveChest(room, contract.getChestIds());
            if (chest != null) chestById.put(chest.getId(), chest);

            for (InteractionWiredContract.Term term : contract.getTerms()) {
                if (term.amount <= 0) continue;

                if (term.direction == InteractionWiredContract.DIR_PAY) {
                    payTotal.merge(term.currencyType, (long) term.amount, Long::sum);
                } else if (chest != null) {
                    chestTakeTotal
                            .computeIfAbsent(chest.getId(), k -> new HashMap<>())
                            .merge(term.currencyType, (long) term.amount, Long::sum);
                }
                // RECEIVE without a linked chest = direct mint (no precondition needed).
            }
        }

        // --- Pre-check: nothing is mutated yet ---
        for (Map.Entry<Integer, Long> entry : payTotal.entrySet()) {
            if (balance(actor, entry.getKey()) < entry.getValue()) {
                this.fire(ctx, room, WiredEvent.Type.TRANSACTION_FAIL);
                return;
            }
        }
        for (Map.Entry<Integer, Map<Integer, Long>> chestEntry : chestTakeTotal.entrySet()) {
            InteractionWiredChest chest = chestById.get(chestEntry.getKey());
            if (chest == null) {
                this.fire(ctx, room, WiredEvent.Type.TRANSACTION_FAIL);
                return;
            }
            for (Map.Entry<Integer, Long> typeEntry : chestEntry.getValue().entrySet()) {
                if (chest.getContents().count(ChestStorage.KIND_CURRENCY, typeEntry.getKey()) < typeEntry.getValue()) {
                    this.fire(ctx, room, WiredEvent.Type.TRANSACTION_FAIL);
                    return;
                }
            }
        }

        // --- Commit: all preconditions passed ---
        for (InteractionWiredContract contract : contracts) {
            InteractionWiredChest chest = this.resolveChest(room, contract.getChestIds());
            boolean chestDirty = false;

            for (InteractionWiredContract.Term term : contract.getTerms()) {
                if (term.amount <= 0) continue;

                if (term.direction == InteractionWiredContract.DIR_PAY) {
                    debit(actor, term.currencyType, term.amount);
                    if (chest != null) {
                        chest.getContents().add(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
                        chestDirty = true;
                    }
                } else {
                    if (chest != null) {
                        chest.getContents().take(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
                        chestDirty = true;
                    }
                    credit(actor, term.currencyType, term.amount);
                }
            }

            if (chestDirty && chest != null) chest.persistContents();
        }

        this.fire(ctx, room, WiredEvent.Type.TRANSACTION_COMPLETE);
    }

    private InteractionWiredChest resolveChest(Room room, List<Integer> ids) {
        if (ids == null) return null;
        for (Integer id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest chest) return chest;
        }
        return null;
    }

    private static int balance(Habbo habbo, int currencyType) {
        return (currencyType < 0)
                ? habbo.getHabboInfo().getCredits()
                : habbo.getHabboInfo().getCurrencyAmount(currencyType);
    }

    private static void debit(Habbo habbo, int currencyType, int amount) {
        if (currencyType < 0) {
            habbo.giveCredits(-amount);
        } else {
            habbo.givePoints(currencyType, -amount);
        }
    }

    private static void credit(Habbo habbo, int currencyType, int amount) {
        if (currencyType < 0) {
            habbo.giveCredits(amount);
        } else {
            habbo.givePoints(currencyType, amount);
        }
    }

    private void fire(WiredContext ctx, Room room, WiredEvent.Type eventType) {
        RoomTile tile = (room.getLayout() != null) ? room.getLayout().getTile(this.getX(), this.getY()) : null;
        WiredEvent.Builder builder = WiredEvent.builder(eventType, room).sourceItem(this);
        if (tile != null) builder.tile(tile);
        ctx.actor().ifPresent(builder::actor);
        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        this.contractIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.contractIds.add(id);
        }
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.contractIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.setDelay(data.delay);
        if (data.contractIds != null) this.contractIds.addAll(data.contractIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.contractIds.size());
        for (Integer id : this.contractIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.contractIds.clear();
        this.setDelay(0);
    }

    static class JsonData {
        int delay;
        List<Integer> contractIds;

        public JsonData(int delay, List<Integer> contractIds) {
            this.delay = delay;
            this.contractIds = contractIds;
        }
    }
}
