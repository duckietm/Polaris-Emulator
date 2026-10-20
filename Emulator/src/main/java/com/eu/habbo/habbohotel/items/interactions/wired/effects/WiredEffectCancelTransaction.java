package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cancel Transaction (furni classname {@code wf_act_cancel_transaction}, official 48, AIR
 * {@code chests/class_4137}): ends the triggering user's running payment or trade. Ints {@code [match
 * criteria]}: 0 only a negotiation started from one of the contracts of furni slot 0, 1 any running one
 * (the contract picks are then not used). Ending it hands the offer back and fires the
 * {@code wf_trg_transaction_fail} triggers, as any cancelled negotiation does.
 *
 * <p>With an Initiate Transaction stacked on top (later in the stack, same delay) the negotiation it opens replaces
 * the cancelled one in the open trade window instead of the window closing and opening again (AIR
 * {@code cancel_transaction.usage_info}); see {@link WiredTradingManager#cancelForReplacement}.
 *
 * <p>Rows saved before the match criteria raised {@link WiredEvent.Type#TRANSACTION_FAIL} every time
 * (the v1 failure signal); they still do when there is nothing to cancel, until the box is saved again.
 */
public class WiredEffectCancelTransaction extends InteractionWiredEffect implements WiredOfficialSources {
    public static final int MATCH_SPECIFIC_CONTRACT = 0;
    public static final int MATCH_ANY = 1;

    static final int DEFAULT_CONTRACT_SOURCE = WiredSourceUtil.SOURCE_SELECTED;

    public static final WiredEffectType type = WiredEffectType.CANCEL_TRANSACTION;

    private final List<Integer> contractIds = new ArrayList<>();
    private int matchCriteria = MATCH_SPECIFIC_CONTRACT;
    private int contractSource = DEFAULT_CONTRACT_SOURCE;
    private boolean legacySignal;

    public WiredEffectCancelTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectCancelTransaction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        Habbo habbo = ctx.actor().map(room::getHabbo).orElse(null);
        boolean cancelled = false;
        if (habbo != null && room.getWiredRuntime() != null) {
            WiredTradingManager trading = room.getWiredRuntime().getTradingManager();
            List<Integer> matched = this.matchedContracts(ctx, room);
            // AIR: an Initiate Transaction stacked on top replaces the transaction without refreshing the window.
            cancelled = this.replacedInSamePass(ctx)
                    ? trading.cancelForReplacement(habbo, matched)
                    : trading.cancelRunning(habbo, matched);
        }
        if (cancelled || !this.legacySignal) return;

        RoomTile tile = room.getLayout().getTile(this.getX(), this.getY());
        WiredEvent.Builder builder =
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room)
                        .sourceItem(this)
                        .transactionFailureReason(ChestTransactionFailures.TRADE_CANCELLED);
        if (tile != null) builder.tile(tile);
        ctx.actor().ifPresent(builder::actor);

        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }

    /**
     * Whether an Initiate Transaction is stacked on top of this box: later in the same stack and run in the same pass
     * (the same delay), so the negotiation it opens replaces the one cancelled here.
     */
    boolean replacedInSamePass(WiredContext ctx) {
        if (ctx == null || !ctx.hasStack() || ctx.stack().effects() == null) return false;

        boolean after = false;
        for (IWiredEffect effect : ctx.stack().effects()) {
            if (effect == this) {
                after = true;
                continue;
            }
            if (after
                    && effect instanceof WiredEffectInitTransaction initiate
                    && initiate.getDelay() == this.getDelay()) return true;
        }
        return false;
    }

    /** The contracts a negotiation must come from to be cancelled; null when any will do. */
    List<Integer> matchedContracts(WiredContext ctx, Room room) {
        if (this.matchCriteria == MATCH_ANY) return null;

        List<HabboItem> picked = new ArrayList<>();
        for (Integer id : this.contractIds) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) picked.add(item);
        }
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.contractSource, picked)) {
            if (item instanceof InteractionWiredContract && !ids.contains(item.getId())) ids.add(item.getId());
        }
        return ids;
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
        int[] params = settings.getIntParams();
        this.matchCriteria = normalizeMatch(params.length > 0 ? params[0] : MATCH_SPECIFIC_CONTRACT);
        this.contractSource = WiredChestTargets.normalizeChestSource(settings.furniSource(0, DEFAULT_CONTRACT_SOURCE));

        this.contractIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (id > 0 && !this.contractIds.contains(id)) this.contractIds.add(id);
            }
        }
        this.legacySignal = false;
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(this.getDelay());
        data.matchCriteria = this.matchCriteria;
        data.contractIds = new ArrayList<>(this.contractIds);
        data.furniSource = this.contractSource;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.setDelay(data.delay);
        // A row saved before the settings keeps the old failure signal.
        if (data.matchCriteria == null) {
            this.legacySignal = true;
            return;
        }

        this.matchCriteria = normalizeMatch(data.matchCriteria);
        if (data.furniSource != null) this.contractSource = WiredChestTargets.normalizeChestSource(data.furniSource);
        if (data.contractIds != null) {
            for (Integer id : data.contractIds) {
                if (id != null && id > 0 && !this.contractIds.contains(id)) this.contractIds.add(id);
            }
        }
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
        message.appendInt(1);
        message.appendInt(this.matchCriteria);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.contractSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public void onPickUp() {
        this.contractIds.clear();
        this.matchCriteria = MATCH_SPECIFIC_CONTRACT;
        this.contractSource = DEFAULT_CONTRACT_SOURCE;
        this.legacySignal = false;
        this.setDelay(0);
    }

    static int normalizeMatch(int value) {
        return value == MATCH_ANY ? MATCH_ANY : MATCH_SPECIFIC_CONTRACT;
    }

    static class JsonData {
        int delay;
        Integer matchCriteria;
        List<Integer> contractIds;
        Integer furniSource;

        public JsonData(int delay) {
            this.delay = delay;
        }
    }
}
