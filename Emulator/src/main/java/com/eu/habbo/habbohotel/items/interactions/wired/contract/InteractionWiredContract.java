package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for gen-3 / Origins wired CONTRACT furni (config-holders, NOT stack add-ons). A contract holds
 * only the TERMS of a transaction — a list of currency {@link Term}s (PAY = debit the user, RECEIVE =
 * credit the user) plus optional linked chest(s) used as the deposit sink (PAY) / source pool (RECEIVE).
 * Contracts never execute on their own; they are selected and EXECUTED atomically by the upgraded
 * {@code WiredEffectInitTransaction}.
 *
 * <p>Concrete subclasses (payment / reward / trade / custom) differ only in their client dialog
 * {@link #contractCode()} — the wire + persistence shape is identical so Init Transaction reads them
 * uniformly. Currency-only v1 (credits {@code type<0} / points {@code type>=0}); furni terms are a
 * future extension (the existing give-furni-from-chest primitive covers furni rewards meanwhile).</p>
 */
public abstract class InteractionWiredContract extends InteractionWiredExtra {
    public static final int DIR_PAY = 0;
    public static final int DIR_RECEIVE = 1;

    private static final int MAX_TERMS = 8;

    protected final List<Term> terms = new ArrayList<>();
    protected final List<Integer> chestIds = new ArrayList<>();

    protected InteractionWiredContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The client {@code WiredActionLayoutCode} for this contract's dialog (110-113). */
    protected abstract int contractCode();

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public List<Term> getTerms() {
        return this.terms;
    }

    public List<Integer> getChestIds() {
        return this.chestIds;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        this.terms.clear();
        if (params.length > 0) {
            int count = Math.max(0, Math.min(MAX_TERMS, params[0]));
            for (int i = 0; i < count; i++) {
                int base = 1 + (i * 3);
                if (base + 2 >= params.length) break;
                int dir = (params[base] == DIR_RECEIVE) ? DIR_RECEIVE : DIR_PAY;
                int type = params[base + 1];
                int amount = Math.max(0, params[base + 2]);
                if (amount > 0) this.terms.add(new Term(dir, type, amount));
            }
        }

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.chestIds.add(id);
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.terms, this.chestIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        if (data.terms != null) {
            for (Term t : data.terms) {
                if (t != null && t.amount > 0) this.terms.add(t);
            }
        }
        if (data.chestIds != null) this.chestIds.addAll(data.chestIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) message.appendInt(id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");

        message.appendInt(1 + (this.terms.size() * 3));
        message.appendInt(this.terms.size());
        for (Term t : this.terms) {
            message.appendInt(t.direction);
            message.appendInt(t.currencyType);
            message.appendInt(t.amount);
        }

        message.appendInt(0);
        message.appendInt(this.contractCode());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.terms.clear();
        this.chestIds.clear();
    }

    /** A single currency term. {@code currencyType}: -1 = credits, >=0 = points/seasonal type. */
    public static class Term {
        public int direction;
        public int currencyType;
        public int amount;

        public Term() {
        }

        public Term(int direction, int currencyType, int amount) {
            this.direction = direction;
            this.currencyType = currencyType;
            this.amount = amount;
        }
    }

    static class JsonData {
        List<Term> terms;
        List<Integer> chestIds;

        JsonData() {
        }

        JsonData(List<Term> terms, List<Integer> chestIds) {
            this.terms = terms;
            this.chestIds = chestIds;
        }
    }
}
