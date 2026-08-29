package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.WiredCompatibilityDiagnostics;
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

    public static final int TYPE_PAYMENT = 0;
    public static final int TYPE_TRADE = 1;
    public static final int TYPE_REWARD = 2;

    public static final int KIND_CURRENCY = 0;
    public static final int KIND_FURNI = 1;

    private static final int MAX_TERMS = 8;

    /**
     * Opens the alternatives format on the save path. A client that predates it sends a plain term
     * count here instead, which can never be negative, so the two shapes cannot be confused.
     */
    public static final int RULES_FORMAT = -1;

    private static final int MAX_RULES = 8;
    private static final int NODE_STRIDE = 5;

    /**
     * The alternatives a player may satisfy, and what the contract hands back. These are the truth;
     * {@link #terms} is the same content flattened, which is what the older instant path reads.
     */
    protected final List<List<Term>> giveRules = new ArrayList<>();

    protected final List<Term> getRule = new ArrayList<>();
    protected final List<Term> terms = new ArrayList<>();
    protected final List<Integer> chestIds = new ArrayList<>();

    protected InteractionWiredContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredContract(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The client {@code WiredActionLayoutCode} for this contract's dialog (110-113). */
    protected abstract int contractCode();

    /**
     * Which of the three official shapes this contract is, for the negotiation window: 0 payment,
     * 1 trade, 2 reward. A custom contract is negotiated like a payment, because that is what it is
     * from the player's side -- give something, get something.
     */
    public int contractType() {
        return switch (contractCode()) {
            case 112 -> TYPE_TRADE;
            case 111 -> TYPE_REWARD;
            default -> TYPE_PAYMENT;
        };
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public List<Term> getTerms() {
        return this.terms;
    }

    /** The alternatives, in the owner's order: the first one a player can pay is the one they pay. */
    public List<List<Term>> getGiveRules() {
        return this.giveRules;
    }

    /** What the contract hands back. All of it, together. */
    public List<Term> getGetRule() {
        return this.getRule;
    }

    /**
     * Rebuild {@link #terms} from the rules. One truth in memory, one derived view, so the two can
     * never drift into disagreeing about what the contract costs.
     */
    protected void rebuildFlatTerms() {
        this.terms.clear();
        for (List<Term> rule : this.giveRules) this.terms.addAll(rule);
        this.terms.addAll(this.getRule);
    }

    public List<Integer> getChestIds() {
        return this.chestIds;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        this.giveRules.clear();
        this.getRule.clear();

        if (params.length > 0 && params[0] == RULES_FORMAT) {
            readRules(params);
        } else {
            readLegacyTerms(params);
        }

        if (this.giveRules.isEmpty()) this.giveRules.add(new ArrayList<>());
        applyPosterIds(settings.getStringParam());
        rebuildFlatTerms();

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.chestIds.add(id);
        }

        return true;
    }

    /**
     * The alternatives format: {@code [-1, ruleCount, (nodeCount, node*)..., rewardCount, node*]},
     * where a node is {@code kind, currencyType, wallItem, baseItemId, amount}. Every count is bounded
     * before it is used, so a crafted payload cannot make the save path read past its own array.
     */
    private void readRules(int[] params) {
        if (params.length < 2) return;

        int ruleCount = Math.max(0, Math.min(MAX_RULES, params[1]));
        int cursor = 2;

        for (int rule = 0; rule < ruleCount; rule++) {
            if (cursor >= params.length) return;

            int nodeCount = Math.max(0, Math.min(MAX_TERMS, params[cursor++]));
            List<Term> nodes = new ArrayList<>();
            for (int node = 0; node < nodeCount; node++) {
                if (cursor + NODE_STRIDE > params.length) return;
                Term term = readNode(params, cursor, DIR_PAY);
                cursor += NODE_STRIDE;
                if (term != null) nodes.add(term);
            }
            this.giveRules.add(nodes);
        }

        if (cursor >= params.length) return;

        int rewardCount = Math.max(0, Math.min(MAX_TERMS, params[cursor++]));
        for (int node = 0; node < rewardCount; node++) {
            if (cursor + NODE_STRIDE > params.length) return;
            Term term = readNode(params, cursor, DIR_RECEIVE);
            cursor += NODE_STRIDE;
            if (term != null) this.getRule.add(term);
        }
    }

    private static Term readNode(int[] params, int base, int direction) {
        int amount = Math.max(0, params[base + 4]);
        if (amount <= 0) return null;

        return params[base] == KIND_FURNI
                ? Term.furni(direction, params[base + 2] != 0, params[base + 3], "", amount)
                : Term.currency(direction, params[base + 1], amount);
    }

    /** The shape a client sent before alternatives existed: one flat list, all of it required. */
    private void readLegacyTerms(int[] params) {
        if (params.length == 0) return;

        List<Term> pay = new ArrayList<>();
        int count = Math.max(0, Math.min(MAX_TERMS, params[0]));
        for (int i = 0; i < count; i++) {
            int base = 1 + (i * 3);
            if (base + 2 >= params.length) break;

            int dir = (params[base] == DIR_RECEIVE) ? DIR_RECEIVE : DIR_PAY;
            int amount = Math.max(0, params[base + 2]);
            if (amount <= 0) continue;

            Term term = Term.currency(dir, params[base + 1], amount);
            if (dir == DIR_RECEIVE) this.getRule.add(term);
            else pay.add(term);
        }
        this.giveRules.add(pay);
    }

    /**
     * Wall posters cannot be identified by base item id alone, so their id rides in the string param
     * as {@code index=poster} pairs against the flattened order.
     */
    private void applyPosterIds(String stringParam) {
        if (stringParam == null || stringParam.isEmpty()) return;

        List<Term> flat = new ArrayList<>();
        for (List<Term> rule : this.giveRules) flat.addAll(rule);
        flat.addAll(this.getRule);

        for (String pair : stringParam.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                int index = Integer.parseInt(pair.substring(0, eq).trim());
                if (index >= 0 && index < flat.size()) flat.get(index).posterId = pair.substring(eq + 1);
            } catch (NumberFormatException malformedIndex) {
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.CONTRACT_POSTER_INDEX, malformedIndex);
            }
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.terms, this.chestIds, this.giveRules, this.getRule));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        applyWiredData(set.getString("wired_data"));
    }

    /**
     * Read a persisted payload. Split out of {@link #loadWiredData} so the migration it performs --
     * a contract written before alternatives existed becoming a single alternative -- can be proven
     * without standing up a database row.
     */
    void applyWiredData(String wiredData) {
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        if (data.giveRules != null || data.getRule != null) {
            if (data.giveRules != null) {
                for (List<Term> rule : data.giveRules) {
                    if (rule != null) this.giveRules.add(sanitise(rule));
                }
            }
            if (data.getRule != null) this.getRule.addAll(sanitise(data.getRule));
        } else if (data.terms != null) {
            // Written before alternatives existed: every PAY term was required together, so they are
            // one alternative. Reading them as several would quietly make the contract cheaper.
            List<Term> pay = new ArrayList<>();
            for (Term term : sanitise(data.terms)) {
                if (term.direction == DIR_RECEIVE) this.getRule.add(term);
                else pay.add(term);
            }
            this.giveRules.add(pay);
        }

        if (this.giveRules.isEmpty()) this.giveRules.add(new ArrayList<>());
        rebuildFlatTerms();

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
        this.giveRules.clear();
        this.getRule.clear();
        this.terms.clear();
        this.chestIds.clear();
    }

    /** Drop anything that would not survive its own accessors: nulls and zero-amount terms. */
    private static List<Term> sanitise(List<Term> rule) {
        List<Term> out = new ArrayList<>();
        for (Term term : rule) {
            if (term != null && term.amount > 0) out.add(term);
        }
        return out;
    }

    /**
     * One thing a contract asks for or hands back: a pile of currency, or a pile of one kind of furni.
     *
     * <p>{@code currencyType}: -1 = credits, >=0 = points/seasonal type. {@code kind} defaults to
     * {@link #KIND_CURRENCY}, which is what makes a term saved before furni existed read back
     * correctly — the field is simply absent from that JSON and lands on zero.
     */
    public static class Term {
        public int direction;
        public int currencyType;
        public int amount;
        public int kind;
        public boolean wallItem;
        public int baseItemId;
        public String posterId;

        public Term() {}

        public Term(int direction, int currencyType, int amount) {
            this.direction = direction;
            this.currencyType = currencyType;
            this.amount = amount;
            this.kind = KIND_CURRENCY;
        }

        public static Term currency(int direction, int currencyType, int amount) {
            return new Term(direction, currencyType, amount);
        }

        public static Term furni(int direction, boolean wallItem, int baseItemId, String posterId, int amount) {
            Term term = new Term();
            term.direction = (direction == DIR_RECEIVE) ? DIR_RECEIVE : DIR_PAY;
            term.kind = KIND_FURNI;
            term.wallItem = wallItem;
            term.baseItemId = baseItemId;
            term.posterId = posterId == null ? "" : posterId;
            term.amount = Math.max(0, amount);
            return term;
        }

        public boolean isCurrency() {
            return this.kind != KIND_FURNI;
        }

        public boolean isFurni() {
            return this.kind == KIND_FURNI;
        }

        public String posterId() {
            return this.posterId == null ? "" : this.posterId;
        }
    }

    /**
     * The persisted shape.
     *
     * <p>{@code giveRules} and {@code getRule} are the truth; {@code terms} is the same content
     * flattened, kept so an older build reading this payload still finds the contract it understands
     * instead of an empty one. A payload written before rules existed has neither field, and its flat
     * {@code terms} then read as a single alternative — which is what they always meant.
     */
    static class JsonData {
        List<Term> terms;
        List<Integer> chestIds;
        List<List<Term>> giveRules;
        List<Term> getRule;

        JsonData() {}

        JsonData(List<Term> terms, List<Integer> chestIds, List<List<Term>> giveRules, List<Term> getRule) {
            this.terms = terms;
            this.chestIds = chestIds;
            this.giveRules = giveRules;
            this.getRule = getRule;
        }
    }
}
