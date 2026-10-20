package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Contract add-on ({@code wf_xtra_custom_contract}, official addon 20, AIR {@code chests/class_4234}): a contract
 * whose payment and reward are each one element -- coins or furni, a fixed amount or a global / context variable --
 * so it can act as a payment, trade or reward contract ({@code wiredfurni.params.custom_contract.usage_warning}). It
 * is edited in the wired dialog, as in AIR.
 *
 * <pre>
 *   ints [payment on, payment type (0 coins, 1 furni), from a variable, amount (1..100000), variable target,
 *         reward on, reward type, from a variable, amount, variable target]
 *   variable ids [payment variable, reward variable]
 *   furni slot 0 the payment furni (first picks), slot 1 the reward furni (second picks)
 * </pre>
 *
 * <p>A furni element asks for (or hands out) the type of the first furni its slot resolves to, so a selector or a
 * signal can choose the furni at run time; a variable amount is read when the transaction starts. When an element
 * that is on cannot be resolved (no furni, a variable without a value), the transaction fails rather than run with
 * half a contract.
 *
 * <p>Contracts saved by our older free-form editor (any number of alternatives and nodes, any currency) keep that
 * shape, payload and editor until they are saved in the official form. One the official form can say exactly -- at
 * most one credits node to pay and one to receive, no chests picked on it -- opens in the official form.
 */
public class InteractionWiredCustomContract extends InteractionWiredContract
        implements WiredOfficialSources, WiredOfficialVariableIds, WiredOfficialSecondaryPicks {
    public static final int CODE = 113;

    public static final int ELEMENT_COINS = 0;
    public static final int ELEMENT_FURNI = 1;
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 100_000;

    static final int OFFICIAL_PARAMS = 10;
    static final int DEFAULT_PAYMENT_SOURCE = WiredSourceUtil.SOURCE_SELECTED;
    static final int DEFAULT_REWARD_SOURCE = WiredVariableOperand.SOURCE_SECONDARY_SELECTED;

    private static final String MISSING_VARIABLE = "wiredfurni.params.variables.validation.missing_variable";

    /** False while the contract is in our older free-form shape. */
    private boolean official;

    private final Element payment = new Element();
    private final Element reward = new Element();
    private int paymentSource = DEFAULT_PAYMENT_SOURCE;
    private int rewardSource = DEFAULT_REWARD_SOURCE;
    private final List<Integer> paymentFurni = new ArrayList<>();
    private final List<Integer> rewardFurni = new ArrayList<>();

    public InteractionWiredCustomContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredCustomContract(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }

    /** One half of the contract: what the player pays, or what they receive. */
    static final class Element {
        boolean enabled;
        int type = ELEMENT_COINS;
        boolean fromVariable;
        int amount = MIN_AMOUNT;
        int target = WiredValueVariable.TARGET_GLOBAL;
        int variableId;

        void reset() {
            this.enabled = false;
            this.type = ELEMENT_COINS;
            this.fromVariable = false;
            this.amount = MIN_AMOUNT;
            this.target = WiredValueVariable.TARGET_GLOBAL;
            this.variableId = 0;
        }

        void read(int[] params, int offset, int variableId) {
            this.enabled = params[offset] == 1;
            this.type = params[offset + 1] == ELEMENT_FURNI ? ELEMENT_FURNI : ELEMENT_COINS;
            this.fromVariable = params[offset + 2] == 1 && variableId > 0;
            this.variableId = this.fromVariable ? variableId : 0;
            this.amount = clampAmount(params[offset + 3]);
            this.target = params[offset + 4] == WiredValueVariable.TARGET_CONTEXT
                    ? WiredValueVariable.TARGET_CONTEXT
                    : WiredValueVariable.TARGET_GLOBAL;
        }

        void write(int[] params, int offset) {
            params[offset] = this.enabled ? 1 : 0;
            params[offset + 1] = this.type;
            params[offset + 2] = this.fromVariable ? 1 : 0;
            params[offset + 3] = this.amount;
            params[offset + 4] = this.target;
        }

        void copyFrom(Stored stored) {
            if (stored == null) return;
            this.enabled = stored.enabled;
            this.type = stored.type == ELEMENT_FURNI ? ELEMENT_FURNI : ELEMENT_COINS;
            this.variableId = Math.max(0, stored.variableId);
            this.fromVariable = stored.fromVariable && this.variableId > 0;
            if (!this.fromVariable) this.variableId = 0;
            this.amount = clampAmount(stored.amount);
            this.target = stored.target == WiredValueVariable.TARGET_CONTEXT
                    ? WiredValueVariable.TARGET_CONTEXT
                    : WiredValueVariable.TARGET_GLOBAL;
        }

        Stored stored() {
            Stored stored = new Stored();
            stored.enabled = this.enabled;
            stored.type = this.type;
            stored.fromVariable = this.fromVariable;
            stored.amount = this.amount;
            stored.target = this.target;
            stored.variableId = this.variableId;
            return stored;
        }
    }

    static int clampAmount(int amount) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount));
    }

    /** Whether the contract is in the official form (the AIR editor), not our older free-form one. */
    public boolean isOfficialForm() {
        return this.official;
    }

    /** Whether {@code params} are the official editor's ten ints (the free-form editor starts with -1 or a count). */
    static boolean isOfficialParams(int[] params) {
        return params != null
                && params.length == OFFICIAL_PARAMS
                && (params[0] == 0 || params[0] == 1)
                && (params[5] == 0 || params[5] == 1);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (!isOfficialParams(params)) {
            // Our older editor: the free-form rules, as they always were saved.
            boolean saved = super.saveData(settings, gameClient);
            this.official = false;
            this.clearOfficial();
            return saved;
        }

        String[] variableIds = settings.getVariableIds();
        // The variable list's ids (room:12); bare box ids from an older client still read.
        int paymentVariable = variableIds.length > 0 ? WiredVariableIds.boxIdOf(variableIds[0]) : 0;
        int rewardVariable = variableIds.length > 1 ? WiredVariableIds.boxIdOf(variableIds[1]) : 0;
        Element nextPayment = new Element();
        Element nextReward = new Element();
        nextPayment.read(params, 0, paymentVariable);
        nextReward.read(params, 5, rewardVariable);
        if ((nextPayment.enabled && params[2] == 1 && !nextPayment.fromVariable)
                || (nextReward.enabled && params[7] == 1 && !nextReward.fromVariable)) {
            throw new WiredSaveException(MISSING_VARIABLE);
        }

        copy(this.payment, nextPayment);
        copy(this.reward, nextReward);
        this.paymentSource = normalizeSource(settings.furniSource(0, DEFAULT_PAYMENT_SOURCE), DEFAULT_PAYMENT_SOURCE);
        this.rewardSource = normalizeSource(settings.furniSource(1, DEFAULT_REWARD_SOURCE), DEFAULT_REWARD_SOURCE);
        copyPicks(this.paymentFurni, settings.getFurniIds());
        copyPicks(this.rewardFurni, settings.getFurniIds2());

        // The free-form rules give way to the two elements; chests picked on the contract stay where they are.
        this.giveRules.clear();
        this.giveRules.add(new ArrayList<>());
        this.getRule.clear();
        rebuildFlatTerms();
        this.official = true;
        return true;
    }

    private static void copy(Element into, Element from) {
        into.enabled = from.enabled;
        into.type = from.type;
        into.fromVariable = from.fromVariable;
        into.amount = from.amount;
        into.target = from.target;
        into.variableId = from.variableId;
    }

    private static void copyPicks(List<Integer> into, int[] ids) {
        into.clear();
        if (ids == null) return;
        for (int id : ids) {
            if (id > 0 && !into.contains(id)) into.add(id);
        }
    }

    private static void copyPicks(List<Integer> into, List<Integer> ids) {
        into.clear();
        if (ids == null) return;
        for (Integer id : ids) {
            if (id != null && id > 0 && !into.contains(id)) into.add(id);
        }
    }

    static int normalizeSource(int source, int fallback) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_SELECTED,
                    WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL -> source;
            default -> fallback;
        };
    }

    private void clearOfficial() {
        this.payment.reset();
        this.reward.reset();
        this.paymentSource = DEFAULT_PAYMENT_SOURCE;
        this.rewardSource = DEFAULT_REWARD_SOURCE;
        this.paymentFurni.clear();
        this.rewardFurni.clear();
    }

    /**
     * Read the stored contract. A free-form contract the official form can say exactly (at most one credits node to
     * pay, one to receive, both within its amounts, and no chests picked on it) opens in the official form.
     */
    @Override
    void applyWiredData(String wiredData) {
        this.official = false;
        this.clearOfficial();
        super.applyWiredData(wiredData);
        if (wiredData == null || !wiredData.startsWith("{")) return;

        OfficialJson data = WiredManager.getGson().fromJson(wiredData, OfficialJson.class);
        if (data != null && data.customContract != null) {
            Official stored = data.customContract;
            this.payment.copyFrom(stored.payment);
            this.reward.copyFrom(stored.reward);
            this.paymentSource = normalizeSource(stored.paymentSource, DEFAULT_PAYMENT_SOURCE);
            this.rewardSource = normalizeSource(stored.rewardSource, DEFAULT_REWARD_SOURCE);
            copyPicks(this.paymentFurni, stored.paymentFurni);
            copyPicks(this.rewardFurni, stored.rewardFurni);
            this.official = true;
        }
    }

    /**
     * Whether the wired dialog shows the official form: a contract saved in it, or a free-form one the official form
     * says exactly with no chests picked on it. The free-form one keeps its stored payload until it is saved again.
     */
    public boolean showsOfficialForm() {
        return this.official || (this.chestIds.isEmpty() && this.freeFormAsOfficial() != null);
    }

    /** The payment and reward the dialog shows: the official ones, or the free-form rules said as elements. */
    private Element[] shownElements() {
        if (this.official) return new Element[] {this.payment, this.reward};

        Element shownPayment = new Element();
        Element shownReward = new Element();
        Term[] fits = this.freeFormAsOfficial();
        if (fits != null && fits[0] != null) {
            shownPayment.enabled = true;
            shownPayment.amount = fits[0].amount;
        }
        if (fits != null && fits[1] != null) {
            shownReward.enabled = true;
            shownReward.amount = fits[1].amount;
        }
        return new Element[] {shownPayment, shownReward};
    }

    /**
     * The free-form rules as the official form's payment and reward ({@code [paid, received]}, either may be null for
     * "off"), or null when the official form cannot say them: more than one alternative, more than one node a side,
     * furni (the official form names furni by picks in the room), a currency other than credits, or an amount out of
     * range.
     */
    private Term[] freeFormAsOfficial() {
        List<Term> pay = null;
        for (List<Term> rule : this.giveRules) {
            if (rule.isEmpty()) continue;
            if (pay != null) return null;
            pay = rule;
        }
        Term paid = singleCredits(pay);
        Term received = singleCredits(this.getRule);
        if ((pay != null && paid == null) || (!this.getRule.isEmpty() && received == null)) return null;
        return new Term[] {paid, received};
    }

    @Override
    protected boolean editableOnceChestsMove(ContractFurniCatalog catalog) {
        return !this.official && this.freeFormAsOfficial() != null;
    }

    /** The rule as one credits node within the official amounts, or null when it is anything else. */
    private static Term singleCredits(List<Term> rule) {
        if (rule == null || rule.isEmpty()) return null;
        List<Term> merged = mergeSameNodes(rule);
        if (merged.size() != 1) return null;
        Term term = merged.get(0);
        if (term.isFurni() || term.currencyType != CURRENCY_CREDITS) return null;
        if (term.amount < MIN_AMOUNT || term.amount > MAX_AMOUNT) return null;
        return term;
    }

    @Override
    public String getWiredData() {
        if (!this.official) return super.getWiredData();

        OfficialJson data = new OfficialJson();
        data.chestIds = new ArrayList<>(this.chestIds);
        Official stored = new Official();
        stored.payment = this.payment.stored();
        stored.reward = this.reward.stored();
        stored.paymentSource = this.paymentSource;
        stored.rewardSource = this.rewardSource;
        stored.paymentFurni = new ArrayList<>(this.paymentFurni);
        stored.rewardFurni = new ArrayList<>(this.rewardFurni);
        data.customContract = stored;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        if (!this.showsOfficialForm()) {
            super.serializeWiredData(message, room);
            return;
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.paymentFurni.size());
        for (Integer id : this.paymentFurni) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        int[] params = this.officialParams();
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    /** The official ten ints. */
    int[] officialParams() {
        Element[] shown = this.shownElements();
        int[] params = new int[OFFICIAL_PARAMS];
        shown[0].write(params, 0);
        shown[1].write(params, 5);
        return params;
    }

    @Override
    public int[] officialFurniSources() {
        return this.showsOfficialForm() ? new int[] {this.paymentSource, this.rewardSource} : new int[0];
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public String[] officialVariableIds() {
        if (!this.official || (!this.payment.fromVariable && !this.reward.fromVariable)) return new String[0];
        return WiredVariableIds.list(
                this.payment.fromVariable ? WiredVariableIds.toWire(this.payment.target, this.payment.variableId) : "",
                this.reward.fromVariable ? WiredVariableIds.toWire(this.reward.target, this.reward.variableId) : "");
    }

    @Override
    public int[] officialFurniIds2() {
        return this.official
                ? this.rewardFurni.stream().mapToInt(Integer::intValue).toArray()
                : new int[0];
    }

    @Override
    public List<List<Term>> giveRulesFor(WiredContext ctx, Room room) {
        if (!this.official) return super.giveRulesFor(ctx, room);
        if (!this.payment.enabled) return List.of(List.of());

        Term term = this.resolve(this.payment, this.paymentSource, this.paymentFurni, DIR_PAY, ctx, room);
        return term == null ? null : List.of(List.of(term));
    }

    @Override
    public List<Term> getRuleFor(WiredContext ctx, Room room) {
        if (!this.official) return super.getRuleFor(ctx, room);
        if (!this.reward.enabled) return List.of();

        Term term = this.resolve(this.reward, this.rewardSource, this.rewardFurni, DIR_RECEIVE, ctx, room);
        return term == null ? null : List.of(term);
    }

    /** One element as a term for this run, or null when it cannot be resolved. */
    private Term resolve(Element element, int source, List<Integer> picks, int direction, WiredContext ctx, Room room) {
        Integer amount = element.amount;
        if (element.fromVariable) {
            amount = WiredValueVariable.read(
                    ctx,
                    room,
                    element.target,
                    element.variableId,
                    WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_TRIGGER);
        }
        if (amount == null || amount < MIN_AMOUNT) return null;
        int clamped = Math.min(MAX_AMOUNT, amount);

        if (element.type == ELEMENT_COINS) return Term.currency(direction, CURRENCY_CREDITS, clamped);

        HabboItem item = firstFurni(ctx, room, source, picks);
        if (item == null || item.getBaseItem() == null) return null;
        boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
        String poster = wall && "poster".equals(item.getBaseItem().getName()) ? item.getExtradata() : "";
        return Term.furni(direction, wall, item.getBaseItem().getId(), poster, clamped);
    }

    private static HabboItem firstFurni(WiredContext ctx, Room room, int source, List<Integer> picks) {
        if (room == null) return null;
        List<HabboItem> picked = new ArrayList<>();
        for (Integer id : picks) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) picked.add(item);
        }
        List<HabboItem> resolved =
                source == WiredSourceUtil.SOURCE_SELECTED || source == WiredVariableOperand.SOURCE_SECONDARY_SELECTED
                        ? WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, picked)
                        : WiredSourceUtil.resolveItems(ctx, source, picked);
        for (HabboItem item : resolved) {
            if (item != null && item.getBaseItem() != null) return item;
        }
        return null;
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.official = false;
        this.clearOfficial();
    }

    /** One element as stored. */
    static class Stored {
        boolean enabled;
        int type;
        boolean fromVariable;
        int amount = MIN_AMOUNT;
        int target = WiredValueVariable.TARGET_GLOBAL;
        int variableId;
    }

    /** The official form as stored. */
    static class Official {
        Stored payment;
        Stored reward;
        int paymentSource = DEFAULT_PAYMENT_SOURCE;
        int rewardSource = DEFAULT_REWARD_SOURCE;
        List<Integer> paymentFurni;
        List<Integer> rewardFurni;
    }

    /** The payload of an official-form contract: the form, and the chests our older editor picked on it. */
    static class OfficialJson {
        List<Integer> chestIds;
        Official customContract;
    }
}
