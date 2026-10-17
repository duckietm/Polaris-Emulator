package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRules;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredChestPayouts;
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
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
    /** How long a player has to complete a negotiation before the offer lapses. */
    private static final int NEGOTIATION_TIMEOUT_SECONDS = 120;

    /** The artwork the window frames the reward with; the richer layouts come from the dialog later. */
    private static final String LAYOUT_GENERIC = "generic";

    public static final WiredEffectType type = WiredEffectType.INIT_TRANSACTION;

    private final List<Integer> contractIds = new ArrayList<>();

    public WiredEffectInitTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitTransaction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        // AIR: a contract only works with the chests that answer wired and are not locked.
        List<HabboItem> linked = new ArrayList<>();
        for (InteractionWiredContract contract : contracts) {
            for (Integer chestId : contract.getChestIds()) {
                HabboItem item = room.getHabboItem(chestId);
                if (item != null) linked.add(item);
            }
        }
        List<InteractionWiredChest> chests = WiredChestPayouts.usable(linked);

        // A contract that asks the player for something is negotiated in the wired trade window: they
        // put credit furni and furni on the table and confirm.
        ContractRules rules = ContractRules.from(contracts);
        if (!rules.asksForNothing()) {
            room.getWiredRuntime()
                    .getTradingManager()
                    .open(
                            actor,
                            rules,
                            contracts.get(0).contractType(),
                            "",
                            LAYOUT_GENERIC,
                            NEGOTIATION_TIMEOUT_SECONDS,
                            chests);
            return;
        }

        // A reward asks for nothing: it is paid at once, out of the chests and only out of them. When
        // they cannot pay all of it, nothing is paid and the player is told why.
        List<InteractionWiredContract.Term> reward = rules.getRule();
        int failure = WiredChestPayouts.holding(chests, () -> {
            int cover = chests.isEmpty()
                    ? ChestTransactionFailures.NO_OR_LOCKED_CHESTS
                    : WiredChestPayouts.coverFailure(chests, reward);
            if (cover != ChestTransactionFailures.NONE) return cover;

            List<InteractionWiredContract.Term> given = WiredChestPayouts.give(actor, room, chests, reward);
            if (!given.isEmpty() && actor.getClient() != null) {
                actor.getClient().sendResponse(WiredTransactionSuccessComposer.reward(given, "", false));
            }
            return ChestTransactionFailures.NONE;
        });

        if (failure != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(actor, failure);
            this.fire(ctx, room, WiredEvent.Type.TRANSACTION_FAIL);
            return;
        }
        this.fire(ctx, room, WiredEvent.Type.TRANSACTION_COMPLETE);
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
