package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.earnings.WiredEarnings;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractMultiplier;
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
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
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
 * preconditions first, mutate last). AIR runs exactly one contract: with none, or with more than one, the
 * transaction fails with reason 21 ({@link ChestTransactionFailures#MISCONFIG_TOO_MANY_OR_NO_CONTRACTS}). Boxes
 * saved before that rule, which ran without a contract (COMPLETE) or pooled several, now fail with 21 until
 * their builder picks one contract. The asset source/sink is the wired chest ({@link InteractionWiredChest}).</p>
 *
 * <p>Official layout (47, AIR {@code chests/class_4135}): ints {@code [mode, multiplier, multiplier from
 * a variable, variable target (global or context), timeout on, timeout seconds]}, the variable as the
 * definition's first variable id. Mode 0 runs the contract once, 1 exactly the multiplier times, 2 as
 * many times as the offer covers up to the multiplier ({@link ContractMultiplier}); the multiplier is
 * 1..500. The timeout (30..3600 s, 300 by default) ends the negotiation when it lapses; without it the
 * negotiation stays open until the player confirms or walks away ("cancel a trade automatically on
 * timeout"). Furni slot 0 the chests (first picks), slot 1 the contracts (second picks), slot 2 and user
 * slot 1 the variable reference; user slot 0 the trading users.
 */
public class WiredEffectInitTransaction extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialVariableIds, WiredOfficialSecondaryPicks {
    public static final int TIMEOUT_MIN_SECONDS = 30;
    public static final int TIMEOUT_MAX_SECONDS = 3600;
    public static final int TIMEOUT_DEFAULT_SECONDS = 300;

    /** What every box gave before the timeout was a setting; rows saved then keep it. */
    static final int LEGACY_TIMEOUT_SECONDS = 120;

    static final int DEFAULT_CHEST_SOURCE = WiredSourceUtil.SOURCE_SELECTED;
    static final int DEFAULT_CONTRACT_SOURCE = WiredVariableOperand.SOURCE_SECONDARY_SELECTED;
    static final int DEFAULT_REFERENCE_SOURCE = WiredSourceUtil.SOURCE_SELECTOR;

    /** The artwork the window frames the reward with; the richer layouts come from the dialog later. */
    private static final String LAYOUT_GENERIC = "generic";

    /** The source label of a failed transaction on the wired monitor. */
    static final String MONITOR_SOURCE = "init_transaction";

    public static final WiredEffectType type = WiredEffectType.INIT_TRANSACTION;

    private final List<Integer> chestIds = new ArrayList<>();
    private final List<Integer> contractIds = new ArrayList<>();
    private int mode = ContractMultiplier.MODE_NORMAL;
    private int multiplier = ContractMultiplier.MIN;
    private boolean multiplierFromVariable;
    private int multiplierTarget = WiredValueVariable.TARGET_GLOBAL;
    private int multiplierVariableId;
    private boolean timeoutEnabled;
    private int timeoutSeconds = TIMEOUT_DEFAULT_SECONDS;
    private int chestSource = DEFAULT_CHEST_SOURCE;
    private int contractSource = DEFAULT_CONTRACT_SOURCE;
    private int referenceFurniSource = DEFAULT_REFERENCE_SOURCE;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int referenceUserSource = DEFAULT_REFERENCE_SOURCE;

    /** A row from before the two pick lists keeps contracts and chests in one; split by kind once the room is up. */
    private boolean mixedPicks;

    public WiredEffectInitTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitTransaction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        try {
            this.run(ctx);
        } finally {
            // A Cancel Transaction under this box held back its "trade cancelled" for the negotiation opened here;
            // when none opened for the player, the window is told now.
            Room room = ctx.room();
            Habbo actor = room == null ? null : ctx.actor().map(room::getHabbo).orElse(null);
            if (actor != null && room.getWiredRuntime() != null) {
                room.getWiredRuntime().getTradingManager().flushReplacement(actor);
            }
        }
    }

    private void run(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;
        this.splitMixedPicks(room);

        List<InteractionWiredContract> contracts = new ArrayList<>();
        // AIR picks the chests a transaction pays into and out of on this box, next to the contracts; a contract's
        // own chest picks (our older editor) still count as well. Either list may hold either kind (older rows).
        List<HabboItem> linked = new ArrayList<>();
        List<HabboItem> picked = new ArrayList<>(this.resolve(ctx, room, this.chestSource, this.chestIds));
        picked.addAll(this.resolve(ctx, room, this.contractSource, this.contractIds));
        for (HabboItem item : picked) {
            if (item instanceof InteractionWiredContract contract) {
                if (!contracts.contains(contract)) contracts.add(contract);
            } else if (item instanceof InteractionWiredChest && !linked.contains(item)) linked.add(item);
        }

        List<Habbo> traders = new ArrayList<>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && !traders.contains(habbo)) traders.add(habbo);
        }

        // AIR: exactly one contract, else the transaction fails as misconfigured (21).
        int contractRefusal = contractCountRefusal(contracts.size());
        if (contractRefusal != ChestTransactionFailures.NONE) {
            for (Habbo trader : traders) ChestTransactionFailures.send(trader, contractRefusal);
            ChestTransactionFailures.noteOnMonitor(room, contractRefusal, MONITOR_SOURCE, this.getId());
            this.fail(ctx, room, contractRefusal);
            return;
        }

        // The contract needs a user to charge/reward.
        if (traders.isEmpty()) {
            // Nobody to trade with: the box runs from something without a user, which is the setup's doing.
            ChestTransactionFailures.noteOnMonitor(
                    room, ChestTransactionFailures.MISCONFIGURED, MONITOR_SOURCE, this.getId());
            this.fail(ctx, room, ChestTransactionFailures.MISCONFIGURED);
            return;
        }

        // AIR: a contract only works with the chests that answer wired and are not locked.
        for (InteractionWiredContract contract : contracts) {
            for (Integer chestId : contract.getChestIds()) {
                HabboItem item = room.getHabboItem(chestId);
                if (item != null && !linked.contains(item)) linked.add(item);
            }
        }
        List<InteractionWiredChest> chests = WiredChestPayouts.usable(linked);
        List<Integer> started = contracts.stream().map(HabboItem::getId).toList();
        ContractMultiplier runs = this.multiplierFor(ctx, room);
        int timeout = this.negotiationTimeout();

        for (Habbo actor : traders) {
            this.transact(ctx, room, actor, contracts, started, chests, runs, timeout);
        }
    }

    private void transact(
            WiredContext ctx,
            Room room,
            Habbo actor,
            List<InteractionWiredContract> contracts,
            List<Integer> started,
            List<InteractionWiredChest> chests,
            ContractMultiplier runs,
            int timeout) {
        // A payment contract in the official donation mode takes whatever the player offers; it has no
        // amounts for a multiplier to scale.
        if (isDonation(contracts)) {
            room.getWiredRuntime()
                    .getTradingManager()
                    .openDonation(actor, tradeText(contracts), layoutType(contracts), timeout, chests, started);
            return;
        }

        // A contract that asks the player for something is negotiated in the wired trade window: they
        // put credit furni and furni on the table and confirm. The window shows the contract once and the
        // session scales it.
        ContractRules rules = ContractRules.from(contracts, ctx, room);
        if (rules == null) {
            // A custom contract whose furni or variable amount cannot be resolved this run.
            ChestTransactionFailures.noteOnMonitor(
                    room, ChestTransactionFailures.MISCONFIGURED, MONITOR_SOURCE, this.getId());
            this.fail(ctx, room, ChestTransactionFailures.MISCONFIGURED);
            return;
        }
        if (!rules.asksForNothing()) {
            room.getWiredRuntime()
                    .getTradingManager()
                    .open(
                            actor,
                            rules,
                            runs,
                            contracts.get(0).contractType(),
                            tradeText(contracts),
                            layoutType(contracts),
                            timeout,
                            chests,
                            started);
            return;
        }

        // A reward asks for nothing: it is paid at once, out of the chests and only out of them, the
        // multiplier times over. When they cannot pay all of it, nothing is paid and the player is told why.
        List<InteractionWiredContract.Term> reward =
                rules.times(rewardRuns(runs)).getRule();
        int failure = WiredChestPayouts.holding(chests, () -> {
            int cover = chests.isEmpty()
                    ? ChestTransactionFailures.NO_OR_LOCKED_CHESTS
                    : WiredChestPayouts.coverFailure(chests, reward);
            if (cover != ChestTransactionFailures.NONE) return cover;

            List<InteractionWiredContract.Term> given =
                    WiredChestPayouts.give(actor, room, chests, reward, earningsCategory(contracts));
            if (!given.isEmpty() && actor.getClient() != null) {
                actor.getClient()
                        .sendResponse(WiredTransactionSuccessComposer.reward(
                                given, rewardText(contracts), showRewardDialog(contracts)));
            }
            return ChestTransactionFailures.NONE;
        });

        if (failure != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(actor, failure);
            ChestTransactionFailures.noteOnMonitor(room, failure, MONITOR_SOURCE, this.getId());
            this.fail(ctx, room, failure);
            return;
        }
        this.fire(ctx, room, WiredEvent.Type.TRANSACTION_COMPLETE);
    }

    /**
     * How many times a reward that asks for nothing is paid: the fixed multiplier, else once. The auto
     * multiplier counts how often an offer covers the price, and with no price there is nothing to count.
     */
    static int rewardRuns(ContractMultiplier runs) {
        return runs.mode() == ContractMultiplier.MODE_FIXED ? runs.value() : 1;
    }

    /** The box's multiplier this run: the fixed value, or the variable's value held to 1..500. */
    ContractMultiplier multiplierFor(WiredContext ctx, Room room) {
        if (this.mode == ContractMultiplier.MODE_NORMAL) return ContractMultiplier.NONE;
        if (!this.multiplierFromVariable) return new ContractMultiplier(this.mode, this.multiplier);

        Integer value = WiredValueVariable.read(
                ctx,
                room,
                this.multiplierTarget,
                this.multiplierVariableId,
                WiredSourceUtil.SOURCE_TRIGGER,
                WiredSourceUtil.SOURCE_TRIGGER);
        // A variable without a value runs the contract once, as the smallest multiplier.
        return new ContractMultiplier(this.mode, value == null ? ContractMultiplier.MIN : value);
    }

    /** The negotiation's timeout in seconds; zero when the box has none and the offer never lapses. */
    int negotiationTimeout() {
        return this.timeoutEnabled ? this.timeoutSeconds : 0;
    }

    private List<HabboItem> resolve(WiredContext ctx, Room room, int source, List<Integer> picks) {
        List<HabboItem> items = new ArrayList<>();
        if (source == WiredSourceUtil.SOURCE_SELECTED || source == WiredVariableOperand.SOURCE_SECONDARY_SELECTED) {
            for (Integer id : picks) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) items.add(item);
            }
            return WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, items);
        }
        return WiredSourceUtil.resolveItems(ctx, source, items);
    }

    /**
     * Move a contract's own chest picks (our older contract editor) onto the Initiate Transaction boxes that pick
     * the contract, where AIR keeps them, so the official contract editor can open it. Nothing changes at runtime: a
     * box always pooled its own chests with its contracts' chests, so the same chests reach the same negotiations.
     *
     * <p>Refused, with nothing changed, when a box in the room may reach the contract another way than by its picks
     * (a selector, a signal, the trigger: there is no telling which contracts it will hold), when no box picks the
     * contract (the chests would have nowhere to go), or when a box would go over the pick limit.
     *
     * @return true when the chests moved; the contract and the boxes are saved
     */
    public static boolean adoptContractChests(Room room, InteractionWiredContract contract) {
        if (room == null || contract == null || contract.getChestIds().isEmpty()) return false;
        if (room.getRoomSpecialTypes() == null) return false;

        List<WiredEffectInitTransaction> boxes = new ArrayList<>();
        for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects()) {
            if (!(effect instanceof WiredEffectInitTransaction box)) continue;
            if (!isPickSource(box.contractSource)) return false;
            box.splitMixedPicks(room);
            if (!box.chestIds.contains(contract.getId()) && !box.contractIds.contains(contract.getId())) continue;
            // Its own chests come from somewhere else, so picks put on it would not be read.
            if (!isPickSource(box.chestSource)) return false;
            boxes.add(box);
        }
        if (boxes.isEmpty()) return false;

        List<Integer> chests = new ArrayList<>(contract.getChestIds());
        for (WiredEffectInitTransaction box : boxes) {
            long added = chests.stream()
                    .filter(id -> id > 0 && !box.chestIds.contains(id))
                    .distinct()
                    .count();
            if (box.chestIds.size() + added > WiredManager.MAXIMUM_FURNI_SELECTION) return false;
        }

        for (WiredEffectInitTransaction box : boxes) {
            for (Integer id : chests) {
                if (id != null && id > 0 && !box.chestIds.contains(id)) box.chestIds.add(id);
            }
            box.needsUpdate(true);
            if (WiredPlatform.threading() != null) WiredPlatform.threading().run(box);
        }
        contract.getChestIds().clear();
        contract.onChestsAdopted();
        contract.needsUpdate(true);
        if (WiredPlatform.threading() != null) WiredPlatform.threading().run(contract);
        return true;
    }

    private static boolean isPickSource(int source) {
        return source == WiredSourceUtil.SOURCE_SELECTED || source == WiredVariableOperand.SOURCE_SECONDARY_SELECTED;
    }

    /** The chests this box picks (first pick list). */
    List<Integer> chestPicks() {
        return List.copyOf(this.chestIds);
    }

    /** Move an older row's chests out of its one pick list, now that the room can say which is which. */
    private void splitMixedPicks(Room room) {
        if (!this.mixedPicks || room == null) return;
        this.mixedPicks = false;

        List<Integer> chests = new ArrayList<>();
        for (Integer id : this.contractIds) {
            if (room.getHabboItem(id) instanceof InteractionWiredChest) chests.add(id);
        }
        this.contractIds.removeAll(chests);
        this.chestIds.addAll(chests);
    }

    /** Every selected contract is a payment contract in donation mode. */
    static boolean isDonation(List<InteractionWiredContract> contracts) {
        if (contracts.isEmpty()) return false;
        for (InteractionWiredContract contract : contracts) {
            if (!contract.isDonation()) return false;
        }
        return true;
    }

    /**
     * What the trade window tells the player they get: a payment contract's receive text, else a reward contract's
     * reward text (AIR {@code PaymentContract} / {@code RewardContract}).
     */
    static String tradeText(List<InteractionWiredContract> contracts) {
        for (InteractionWiredContract contract : contracts) {
            if (contract.contractType() == InteractionWiredContract.TYPE_PAYMENT
                    && !contract.getReceiveText().isEmpty()) return contract.getReceiveText();
        }
        return rewardText(contracts);
    }

    /** The reward popup's text: the first reward contract's. */
    static String rewardText(List<InteractionWiredContract> contracts) {
        for (InteractionWiredContract contract : contracts) {
            if (contract.contractType() == InteractionWiredContract.TYPE_REWARD
                    && !contract.getRewardText().isEmpty()) return contract.getRewardText();
        }
        return "";
    }

    /** Whether the reward popup opens by itself: any reward contract asks for it. */
    static boolean showRewardDialog(List<InteractionWiredContract> contracts) {
        for (InteractionWiredContract contract : contracts) {
            if (contract.contractType() == InteractionWiredContract.TYPE_REWARD && contract.isShowDialog()) return true;
        }
        return false;
    }

    /**
     * The earnings window row a reward's credits land in: the first reward contract's earnings category (AIR
     * {@code RewardContract}), else games. A trade contract has no category of its own.
     */
    public static int earningsCategory(List<InteractionWiredContract> contracts) {
        if (contracts != null) {
            for (InteractionWiredContract contract : contracts) {
                if (contract != null && contract.contractType() == InteractionWiredContract.TYPE_REWARD)
                    return contract.getRewardCategory();
            }
        }
        return WiredEarnings.CATEGORY_GAMES;
    }

    /** The trade window's artwork: the first payment contract's layout. */
    static String layoutType(List<InteractionWiredContract> contracts) {
        for (InteractionWiredContract contract : contracts) {
            if (contract.contractType() == InteractionWiredContract.TYPE_PAYMENT) return contract.getLayoutType();
        }
        return LAYOUT_GENERIC;
    }

    /**
     * Why the box refuses to run with {@code contracts} resolved contracts: AIR runs exactly one, so none or more
     * than one is {@link ChestTransactionFailures#MISCONFIG_TOO_MANY_OR_NO_CONTRACTS}.
     */
    static int contractCountRefusal(int contracts) {
        return contracts == 1
                ? ChestTransactionFailures.NONE
                : ChestTransactionFailures.MISCONFIG_TOO_MANY_OR_NO_CONTRACTS;
    }

    private void fire(WiredContext ctx, Room room, WiredEvent.Type eventType) {
        this.dispatch(this.event(ctx, room, eventType).build());
    }

    /** Raises TRANSACTION_FAIL carrying the reason, which the stack reads as {@code @event.transaction_failed.reason}. */
    private void fail(WiredContext ctx, Room room, int reason) {
        this.dispatch(this.event(ctx, room, WiredEvent.Type.TRANSACTION_FAIL)
                .transactionFailureReason(reason)
                .build());
    }

    private WiredEvent.Builder event(WiredContext ctx, Room room, WiredEvent.Type eventType) {
        RoomTile tile = (room.getLayout() != null) ? room.getLayout().getTile(this.getX(), this.getY()) : null;
        WiredEvent.Builder builder = WiredEvent.builder(eventType, room).sourceItem(this);
        if (tile != null) builder.tile(tile);
        ctx.actor().ifPresent(builder::actor);
        return builder;
    }

    /** Hands the box's event to the wired engine. */
    void dispatch(WiredEvent event) {
        WiredManager.dispatchEffectTriggeredEvent(event);
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
        String[] variableIds = settings.getVariableIds();
        int variableId = variableIds.length > 0 ? WiredValueVariable.parseId(variableIds[0]) : 0;

        this.mode = ContractMultiplier.normalizeMode(params.length > 0 ? params[0] : ContractMultiplier.MODE_NORMAL);
        this.multiplier = ContractMultiplier.clamp(params.length > 1 ? params[1] : ContractMultiplier.MIN);
        this.multiplierFromVariable = params.length > 2 && params[2] == 1 && variableId > 0;
        this.multiplierVariableId = this.multiplierFromVariable ? variableId : 0;
        this.multiplierTarget =
                WiredChestReward.normalizeTarget(params.length > 3 ? params[3] : WiredValueVariable.TARGET_GLOBAL);
        this.timeoutEnabled = params.length > 4 && params[4] == 1;
        this.timeoutSeconds = clampTimeout(params.length > 5 ? params[5] : TIMEOUT_DEFAULT_SECONDS);

        this.chestSource = normalizeFurniSource(settings.furniSource(0, DEFAULT_CHEST_SOURCE), DEFAULT_CHEST_SOURCE);
        this.contractSource =
                normalizeFurniSource(settings.furniSource(1, DEFAULT_CONTRACT_SOURCE), DEFAULT_CONTRACT_SOURCE);
        this.referenceFurniSource = settings.furniSource(2, DEFAULT_REFERENCE_SOURCE);
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.referenceUserSource = settings.userSource(1, DEFAULT_REFERENCE_SOURCE);

        copyPicks(this.chestIds, settings.getFurniIds());
        copyPicks(this.contractIds, settings.getFurniIds2());
        this.mixedPicks = false;
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(this.getDelay(), this.contractIds);
        data.chestIds = new ArrayList<>(this.chestIds);
        data.mode = this.mode;
        data.multiplier = this.multiplier;
        data.multiplierFromVariable = this.multiplierFromVariable;
        data.multiplierTarget = this.multiplierTarget;
        data.multiplierVariableId = this.multiplierVariableId;
        data.timeoutEnabled = this.timeoutEnabled;
        data.timeoutSeconds = this.timeoutSeconds;
        data.furniSource = this.chestSource;
        data.furniSource2 = this.contractSource;
        data.furniSource3 = this.referenceFurniSource;
        data.userSource = this.userSource;
        data.userSource2 = this.referenceUserSource;
        return WiredManager.getGson().toJson(data);
    }

    /**
     * Rows saved before the official settings run the contract once and keep the old fixed timeout; their
     * one pick list held contracts and chests together.
     */
    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.setDelay(data.delay);
        if (data.contractIds != null) copyPicks(this.contractIds, data.contractIds);
        if (data.chestIds != null) copyPicks(this.chestIds, data.chestIds);
        else this.mixedPicks = !this.contractIds.isEmpty();

        if (data.mode != null) this.mode = ContractMultiplier.normalizeMode(data.mode);
        if (data.multiplier != null) this.multiplier = ContractMultiplier.clamp(data.multiplier);
        this.multiplierVariableId = Math.max(0, data.multiplierVariableId);
        this.multiplierFromVariable = data.multiplierFromVariable && this.multiplierVariableId > 0;
        if (!this.multiplierFromVariable) this.multiplierVariableId = 0;
        if (data.multiplierTarget != null)
            this.multiplierTarget = WiredChestReward.normalizeTarget(data.multiplierTarget);
        if (data.timeoutSeconds != null) {
            this.timeoutEnabled = data.timeoutEnabled;
            this.timeoutSeconds = clampTimeout(data.timeoutSeconds);
        } else {
            this.timeoutEnabled = true;
            this.timeoutSeconds = LEGACY_TIMEOUT_SECONDS;
        }
        if (data.furniSource != null) this.chestSource = normalizeFurniSource(data.furniSource, DEFAULT_CHEST_SOURCE);
        if (data.furniSource2 != null)
            this.contractSource = normalizeFurniSource(data.furniSource2, DEFAULT_CONTRACT_SOURCE);
        if (data.furniSource3 != null) this.referenceFurniSource = data.furniSource3;
        if (data.userSource != null) this.userSource = data.userSource;
        if (data.userSource2 != null) this.referenceUserSource = data.userSource2;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.splitMixedPicks(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        int[] params = this.officialParams();
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    /** {@code [mode, multiplier, from a variable, target, timeout on, timeout seconds]}. */
    int[] officialParams() {
        return new int[] {
            this.mode,
            this.multiplier,
            this.multiplierFromVariable ? 1 : 0,
            this.multiplierTarget,
            this.timeoutEnabled ? 1 : 0,
            this.timeoutSeconds
        };
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.chestSource, this.contractSource, this.referenceFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.referenceUserSource};
    }

    @Override
    public String[] officialVariableIds() {
        return this.multiplierFromVariable ? new String[] {Integer.toString(this.multiplierVariableId)} : new String[0];
    }

    @Override
    public int[] officialFurniIds2() {
        return this.contractIds.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void onPickUp() {
        this.chestIds.clear();
        this.contractIds.clear();
        this.mode = ContractMultiplier.MODE_NORMAL;
        this.multiplier = ContractMultiplier.MIN;
        this.multiplierFromVariable = false;
        this.multiplierTarget = WiredValueVariable.TARGET_GLOBAL;
        this.multiplierVariableId = 0;
        this.timeoutEnabled = false;
        this.timeoutSeconds = TIMEOUT_DEFAULT_SECONDS;
        this.chestSource = DEFAULT_CHEST_SOURCE;
        this.contractSource = DEFAULT_CONTRACT_SOURCE;
        this.referenceFurniSource = DEFAULT_REFERENCE_SOURCE;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceUserSource = DEFAULT_REFERENCE_SOURCE;
        this.mixedPicks = false;
        this.setDelay(0);
    }

    static int clampTimeout(int seconds) {
        return Math.max(TIMEOUT_MIN_SECONDS, Math.min(TIMEOUT_MAX_SECONDS, seconds));
    }

    /** An official furni source a pick slot may hold; anything else falls back to the slot's default. */
    static int normalizeFurniSource(int source, int fallback) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_SELECTED,
                    WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL -> source;
            default -> fallback;
        };
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
        for (Integer id : ids) {
            if (id != null && id > 0 && !into.contains(id)) into.add(id);
        }
    }

    static class JsonData {
        int delay;
        List<Integer> contractIds;
        List<Integer> chestIds;
        Integer mode;
        Integer multiplier;
        boolean multiplierFromVariable;
        Integer multiplierTarget;
        int multiplierVariableId;
        boolean timeoutEnabled;
        Integer timeoutSeconds;
        Integer furniSource;
        Integer furniSource2;
        Integer furniSource3;
        Integer userSource;
        Integer userSource2;

        public JsonData(int delay, List<Integer> contractIds) {
            this.delay = delay;
            this.contractIds = contractIds;
        }
    }
}
