package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectInitTransaction;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredContractOpenComposer;
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

    /** The credits currency type; the official editor's coin node is credits and nothing else. */
    public static final int CURRENCY_CREDITS = -1;

    // The official contract editor's options (AIR PaymentContract / RewardContract). Stored only when they differ
    // from these defaults, so a contract nobody opened in the new editor keeps its payload byte for byte.
    private int paymentMode = WiredNativeContract.PAYMENT_MODE_SPECIFIC;
    private String receiveText = "";
    private String layoutType = WiredNativeContract.LAYOUT_GENERIC;
    private int rewardCategory = WiredNativeContract.EARNINGS_CATEGORY_DEFAULT;
    private boolean showDialog = false;
    private String rewardText = "";

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

    /** AIR payment mode: {@link WiredNativeContract#PAYMENT_MODE_DONATION} or {@code PAYMENT_MODE_SPECIFIC}. */
    public int getPaymentMode() {
        return this.paymentMode;
    }

    /** A payment contract that takes whatever the player offers instead of its rules. */
    public boolean isDonation() {
        return this.contractType() == TYPE_PAYMENT
                && this.supportsNativeEditor()
                && this.paymentMode == WiredNativeContract.PAYMENT_MODE_DONATION;
    }

    /** What the payment window tells the player they get (AIR "receive text"). */
    public String getReceiveText() {
        return this.receiveText;
    }

    /** The payment window's artwork: {@code generic} or {@code games}. */
    public String getLayoutType() {
        return this.layoutType;
    }

    /** The credit earnings category a reward's credits count towards (11 or 13). */
    public int getRewardCategory() {
        return this.rewardCategory;
    }

    /** Whether the reward popup opens by itself. */
    public boolean isShowDialog() {
        return this.showDialog;
    }

    /** The reward popup's text. */
    public String getRewardText() {
        return this.rewardText;
    }

    /**
     * Payment, trade and reward contracts have the official contract editor; the custom contract is a stack add-on
     * in the official client too and keeps the wired editor.
     */
    public boolean supportsNativeEditor() {
        int code = contractCode();
        return code == InteractionWiredContractPayment.CODE
                || code == InteractionWiredContractTrade.CODE
                || code == InteractionWiredContractReward.CODE;
    }

    /**
     * Whether a click opens the official contract editor. A contract that holds something the official editor cannot
     * show -- linked chests, a currency other than credits, more options or nodes than it allows -- keeps opening the
     * wired editor, so nothing it holds is hidden or lost on a save.
     */
    public boolean opensNativeEditor(ContractFurniCatalog catalog) {
        return supportsNativeEditor() && this.chestIds.isEmpty() && toNative(0, catalog) != null;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        ContractFurniCatalog catalog = ContractFurniCatalog.live();
        // Chests picked on the contract itself (our older editor) move to the Initiate Transaction boxes that use it,
        // where AIR keeps them, when that changes nothing at runtime; then the official editor can show the contract.
        if (client != null
                && room != null
                && !this.chestIds.isEmpty()
                && room.canModifyWired(client.getHabbo())
                && this.editableOnceChestsMove(catalog)) {
            WiredEffectInitTransaction.adoptContractChests(room, this);
        }

        if (client != null && room != null && room.canInspectWired(client.getHabbo()) && opensNativeEditor(catalog)) {
            client.sendResponse(new WiredContractOpenComposer(this.getId()));
            this.activateBox(room);
            return;
        }
        super.onClick(client, room, objects);
    }

    /** Whether the official editor could show this contract once the chests picked on it moved to its boxes. */
    protected boolean editableOnceChestsMove(ContractFurniCatalog catalog) {
        return this.supportsNativeEditor() && toNative(0, catalog) != null;
    }

    /** Called when the chests picked on this contract moved to its Initiate Transaction boxes, before it is saved. */
    public void onChestsAdopted() {}

    /**
     * The alternatives a player may pay this run. The fixed contracts always ask the same; the custom contract can
     * read a variable or a selector. Null when the contract cannot be resolved this run, which fails the transaction.
     */
    public List<List<Term>> giveRulesFor(WiredContext ctx, Room room) {
        return this.giveRules;
    }

    /** What the contract hands back this run; null when it cannot be resolved (see {@link #giveRulesFor}). */
    public List<Term> getRuleFor(WiredContext ctx, Room room) {
        return this.getRule;
    }

    /** This contract as the official editor shows it, or null when it cannot show all of it. */
    public WiredNativeContract toNative(int contractId, ContractFurniCatalog catalog) {
        if (!supportsNativeEditor()) return null;

        int type = contractType();
        List<List<WiredNativeContract.Node>> give = null;
        List<WiredNativeContract.Node> get = null;

        if (type != TYPE_REWARD) {
            give = new ArrayList<>();
            for (List<Term> rule : this.giveRules) {
                if (rule.isEmpty()) continue;
                List<WiredNativeContract.Node> nodes = toNativeRule(rule, catalog);
                if (nodes == null) return null;
                give.add(nodes);
            }
            if (give.size() > WiredNativeContract.MAX_PAYMENT_RULES) return null;
        } else {
            for (List<Term> rule : this.giveRules) {
                if (!rule.isEmpty()) return null;
            }
        }

        if (type != TYPE_PAYMENT) {
            get = toNativeRule(this.getRule, catalog);
            if (get == null) return null;
        } else if (!this.getRule.isEmpty()) {
            return null;
        }

        return new WiredNativeContract(
                contractId,
                type,
                give,
                get,
                this.paymentMode,
                this.receiveText,
                this.layoutType,
                this.rewardCategory,
                this.showDialog,
                this.rewardText);
    }

    /**
     * One rule as the official editor shows it, or null when it cannot: more nodes than AIR allows, a currency other
     * than credits (AIR's coin node is credits only), an amount outside AIR's caps, or furni a contract may not hold.
     * Nodes of the same coins or the same furni are shown as one: a rule is settled on its totals, so "5 coins and 5
     * coins" and "10 coins" cost the same, and a native save then stores the shorter form.
     */
    private static List<WiredNativeContract.Node> toNativeRule(List<Term> rule, ContractFurniCatalog catalog) {
        List<Term> merged = mergeSameNodes(rule);
        if (merged.size() > WiredNativeContract.MAX_NODES) return null;

        List<WiredNativeContract.Node> nodes = new ArrayList<>();
        for (Term term : merged) {
            if (term.isFurni()) {
                if (term.amount < 1 || term.amount > WiredNativeContract.MAX_FURNI) return null;
                if (catalog != null && !catalog.allowedInContract(term.baseItemId)) return null;
                int typeId = catalog == null ? 0 : catalog.typeIdOf(term.baseItemId);
                nodes.add(WiredNativeContract.Node.furni(
                        term.amount, term.wallItem, typeId > 0 ? typeId : term.baseItemId, term.posterId()));
            } else {
                if (term.currencyType != CURRENCY_CREDITS) return null;
                if (term.amount < 1 || term.amount > WiredNativeContract.MAX_COINS) return null;
                nodes.add(WiredNativeContract.Node.coins(term.amount));
            }
        }
        return nodes;
    }

    /**
     * The rule with the terms for the same thing added up, in the order each first appears: one per currency, one
     * per furni type (wall or floor, base item, poster). The evaluator claims offered furni term by term and settles
     * currency on the rule's total, so the merged rule asks for exactly the same.
     */
    static List<Term> mergeSameNodes(List<Term> rule) {
        List<Term> merged = new ArrayList<>();
        if (rule == null) return merged;

        for (Term term : rule) {
            if (term == null || term.amount <= 0) continue;
            Term same = null;
            for (Term existing : merged) {
                if (sameThing(existing, term)) {
                    same = existing;
                    break;
                }
            }
            if (same == null) {
                merged.add(
                        term.isFurni()
                                ? Term.furni(
                                        term.direction, term.wallItem, term.baseItemId, term.posterId(), term.amount)
                                : Term.currency(term.direction, term.currencyType, term.amount));
            } else {
                same.amount = (int) Math.min(Integer.MAX_VALUE, (long) same.amount + term.amount);
            }
        }
        return merged;
    }

    private static boolean sameThing(Term left, Term right) {
        if (left.isFurni() != right.isFurni()) return false;
        if (!left.isFurni()) return left.currencyType == right.currencyType;
        return left.wallItem == right.wallItem
                && left.baseItemId == right.baseItemId
                && left.posterId().equals(right.posterId());
    }

    /**
     * Take what the official editor saved (AIR {@code WiredContractController.saveContract}). Everything is checked
     * before anything changes, with the official editor's limits; linked chests are left as they are.
     *
     * @return false when the contract was refused (the editor shows {@code wiredcontracts.error.invalid_rules})
     */
    public boolean applyNative(WiredNativeContract contract, ContractFurniCatalog catalog) {
        if (contract == null || catalog == null || !supportsNativeEditor()) return false;

        int type = contractType();
        if (contract.contractType() != type || !validOptions(contract, type)) return false;

        List<List<Term>> give = new ArrayList<>();
        if (contract.give() != null) {
            if (contract.give().size() > WiredNativeContract.MAX_PAYMENT_RULES) return false;
            for (List<WiredNativeContract.Node> rule : contract.give()) {
                List<Term> terms = fromNativeRule(rule, DIR_PAY, catalog);
                if (terms == null) return false;
                give.add(terms);
            }
        }
        List<Term> get = List.of();
        if (contract.get() != null) {
            get = fromNativeRule(contract.get(), DIR_RECEIVE, catalog);
            if (get == null) return false;
        }

        this.giveRules.clear();
        this.giveRules.addAll(give);
        if (this.giveRules.isEmpty()) this.giveRules.add(new ArrayList<>());
        this.getRule.clear();
        this.getRule.addAll(get);
        rebuildFlatTerms();

        if (type == TYPE_PAYMENT) {
            this.paymentMode = contract.paymentMode();
            this.receiveText = contract.receiveText();
            this.layoutType = contract.layoutType();
        }
        if (type == TYPE_REWARD) {
            this.rewardCategory = contract.rewardCategory();
            this.showDialog = contract.showDialog();
            this.rewardText = contract.rewardText();
        }
        return true;
    }

    /** Which rules a type carries and the type's own options, as the official editor allows them. */
    private static boolean validOptions(WiredNativeContract contract, int type) {
        return switch (type) {
            case TYPE_PAYMENT ->
                contract.give() != null
                        && contract.get() == null
                        && (contract.paymentMode() == WiredNativeContract.PAYMENT_MODE_DONATION
                                || (contract.paymentMode() == WiredNativeContract.PAYMENT_MODE_SPECIFIC
                                        && !contract.give().isEmpty()))
                        && (WiredNativeContract.LAYOUT_GENERIC.equals(contract.layoutType())
                                || WiredNativeContract.LAYOUT_GAMES.equals(contract.layoutType()))
                        && contract.receiveText().length() <= WiredNativeContract.MAX_RECEIVE_TEXT;
            case TYPE_TRADE -> contract.give() != null && !contract.give().isEmpty() && contract.get() != null;
            default ->
                contract.give() == null
                        && contract.get() != null
                        && (contract.rewardCategory() == WiredNativeContract.EARNINGS_CATEGORY_DEFAULT
                                || contract.rewardCategory() == WiredNativeContract.EARNINGS_CATEGORY_ALTERNATIVE)
                        && contract.rewardText().length() <= WiredNativeContract.MAX_REWARD_TEXT;
        };
    }

    /** One rule from the editor, or null when it breaks the editor's limits or names furni a contract may not hold. */
    private List<Term> fromNativeRule(
            List<WiredNativeContract.Node> rule, int direction, ContractFurniCatalog catalog) {
        if (rule == null || rule.isEmpty() || rule.size() > WiredNativeContract.MAX_NODES) return null;

        List<Term> out = new ArrayList<>();
        for (WiredNativeContract.Node node : rule) {
            if (node.isFurni()) {
                if (node.amount() < 1 || node.amount() > WiredNativeContract.MAX_FURNI) return null;
                int baseItemId = catalog.baseItemIdOf(node.wall(), node.typeId(), preferredBaseItem(node, catalog));
                if (baseItemId <= 0 || !catalog.allowedInContract(baseItemId)) return null;
                out.add(Term.furni(
                        direction, node.wall(), baseItemId, node.wall() ? node.posterId() : "", node.amount()));
            } else {
                if (node.amount() < 1 || node.amount() > WiredNativeContract.MAX_COINS) return null;
                out.add(Term.currency(direction, CURRENCY_CREDITS, node.amount()));
            }
        }
        return out;
    }

    /** The base item this contract already uses for a furni type, so a re-save does not swap it for a twin. */
    private int preferredBaseItem(WiredNativeContract.Node node, ContractFurniCatalog catalog) {
        for (Term term : this.terms) {
            if (term.isFurni() && term.wallItem == node.wall() && catalog.typeIdOf(term.baseItemId) == node.typeId()) {
                return term.baseItemId;
            }
        }
        return 0;
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
     * Write the grammar as int params: {@code [-1, ruleCount, (nodeCount, node*)..., rewardCount,
     * node*]}, a node being {@code kind, currencyType, wallItem, baseItemId, amount}.
     *
     * <p>Shared by the dialog push and read back by {@link #readRules}, so the encode and the decode
     * cannot fall out of step.
     */
    protected int[] buildRuleParams() {
        List<Integer> out = new ArrayList<>();
        out.add(RULES_FORMAT);
        out.add(this.giveRules.size());

        for (List<Term> rule : this.giveRules) {
            out.add(rule.size());
            for (Term term : rule) appendNode(out, term);
        }

        out.add(this.getRule.size());
        for (Term term : this.getRule) appendNode(out, term);

        int[] params = new int[out.size()];
        for (int i = 0; i < params.length; i++) params[i] = out.get(i);
        return params;
    }

    private static void appendNode(List<Integer> out, Term term) {
        out.add(term.isFurni() ? KIND_FURNI : KIND_CURRENCY);
        out.add(term.isFurni() ? 0 : term.currencyType);
        out.add(term.wallItem ? 1 : 0);
        out.add(term.isFurni() ? term.baseItemId : 0);
        out.add(term.amount);
    }

    /**
     * The alternatives format: {@code [-1, ruleCount, (nodeCount, node*)..., rewardCount, node*]},
     * where a node is {@code kind, currencyType, wallItem, baseItemId, amount}. Every count is bounded
     * before it is used, so a crafted payload cannot make the save path read past its own array.
     */
    void readRules(int[] params) {
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
     * The poster ids in the shape the dialog sends them: {@code index=poster} pairs against the
     * flattened order, give rules first and then the get rule. The reopened dialog reads these
     * back, so a wall poster keeps its id across a re-save instead of losing it silently.
     */
    String posterIdParam() {
        List<Term> flat = new ArrayList<>();
        for (List<Term> rule : this.giveRules) flat.addAll(rule);
        flat.addAll(this.getRule);

        StringBuilder out = new StringBuilder();
        for (int index = 0; index < flat.size(); index++) {
            String posterId = flat.get(index).posterId();
            if (posterId.isEmpty()) continue;
            if (out.length() > 0) out.append(',');
            out.append(index).append('=').append(posterId);
        }
        return out.toString();
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
        JsonData data = new JsonData(this.terms, this.chestIds, this.giveRules, this.getRule);
        if (this.paymentMode != WiredNativeContract.PAYMENT_MODE_SPECIFIC) data.paymentMode = this.paymentMode;
        if (!this.receiveText.isEmpty()) data.receiveText = this.receiveText;
        if (!WiredNativeContract.LAYOUT_GENERIC.equals(this.layoutType)) data.layoutType = this.layoutType;
        if (this.rewardCategory != WiredNativeContract.EARNINGS_CATEGORY_DEFAULT)
            data.rewardCategory = this.rewardCategory;
        if (this.showDialog) data.showDialog = true;
        if (!this.rewardText.isEmpty()) data.rewardText = this.rewardText;
        return WiredManager.getGson().toJson(data);
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

        if (data.paymentMode != null && data.paymentMode == WiredNativeContract.PAYMENT_MODE_DONATION)
            this.paymentMode = WiredNativeContract.PAYMENT_MODE_DONATION;
        if (data.receiveText != null) this.receiveText = clip(data.receiveText, WiredNativeContract.MAX_RECEIVE_TEXT);
        if (WiredNativeContract.LAYOUT_GAMES.equals(data.layoutType))
            this.layoutType = WiredNativeContract.LAYOUT_GAMES;
        if (data.rewardCategory != null && data.rewardCategory == WiredNativeContract.EARNINGS_CATEGORY_ALTERNATIVE)
            this.rewardCategory = WiredNativeContract.EARNINGS_CATEGORY_ALTERNATIVE;
        if (data.showDialog != null) this.showDialog = data.showDialog;
        if (data.rewardText != null) this.rewardText = clip(data.rewardText, WiredNativeContract.MAX_REWARD_TEXT);
    }

    private static String clip(String text, int length) {
        return text.length() > length ? text.substring(0, length) : text;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) message.appendInt(id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(posterIdParam());

        // The same array the dialog sends back on save, so the two directions cannot drift into
        // disagreeing about what a contract says -- which is exactly what had gone wrong.
        int[] params = buildRuleParams();
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);

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
        this.paymentMode = WiredNativeContract.PAYMENT_MODE_SPECIFIC;
        this.receiveText = "";
        this.layoutType = WiredNativeContract.LAYOUT_GENERIC;
        this.rewardCategory = WiredNativeContract.EARNINGS_CATEGORY_DEFAULT;
        this.showDialog = false;
        this.rewardText = "";
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

        // The official contract editor's options; absent while at their defaults.
        Integer paymentMode;
        String receiveText;
        String layoutType;
        Integer rewardCategory;
        Boolean showDialog;
        String rewardText;

        JsonData() {}

        JsonData(List<Term> terms, List<Integer> chestIds, List<List<Term>> giveRules, List<Term> getRule) {
            this.terms = terms;
            this.chestIds = chestIds;
            this.giveRules = giveRules;
            this.getRule = getRule;
        }
    }
}
