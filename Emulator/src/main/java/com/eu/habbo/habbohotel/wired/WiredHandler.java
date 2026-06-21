package com.eu.habbo.habbohotel.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecuteInOrder;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredDisableSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRewardAlertComposer;
import com.eu.habbo.plugin.events.furniture.wired.WiredConditionFailedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import com.eu.habbo.plugin.events.users.UserWiredRewardReceived;
import com.eu.habbo.habbohotel.wired.core.WiredExecutionOrderUtil;
import com.google.gson.GsonBuilder;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class WiredHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHandler.class);

    //Configuration. Loaded from database & updated accordingly.
    public static int MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);
    public static int TELEPORT_DELAY = Emulator.getConfig().getInt("wired.effect.teleport.delay", 500);

    private static GsonBuilder gsonBuilder = null;

    private static final class LegacyExecutionPlan {
        private final LinkedHashSet<InteractionWiredEffect> effects = new LinkedHashSet<>();
        private boolean executeInOrder = false;
    }

    public static boolean handle(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (triggerType == WiredTriggerType.CUSTOM) return false;

        boolean talked = false;

        if (!Emulator.isReady)
            return false;

        if (room == null)
            return false;

        if (RoomWiredDisableSupport.isWiredDisabled(room))
            return false;

        if (!room.isLoaded())
            return false;

        if (room.getRoomSpecialTypes() == null)
            return false;

        THashSet<InteractionWiredTrigger> triggers = room.getRoomSpecialTypes().getTriggers(triggerType);

        if (triggers == null || triggers.isEmpty())
            return false;

        long millis = System.currentTimeMillis();
        List<LegacyExecutionPlan> executionPlans = new ArrayList<>();

        LinkedHashSet<Long> triggeredTiles = new LinkedHashSet<>();
        for (InteractionWiredTrigger trigger : triggers) {
            long coordinateKey = toTileCoordinateKey(trigger.getX(), trigger.getY());

            if (!triggeredTiles.add(coordinateKey))
                continue;

            LegacyExecutionPlan executionPlan = new LegacyExecutionPlan();

            if (handle(trigger, roomUnit, room, stuff, executionPlan)) {
                executionPlans.add(executionPlan);

                if (triggerType.equals(WiredTriggerType.SAY_SOMETHING))
                    talked = true;
            }
        }

        for (LegacyExecutionPlan executionPlan : executionPlans) {
            triggerEffects(executionPlan.effects, roomUnit, room, stuff, millis, executionPlan.executeInOrder);
        }

        return talked;
    }

    public static boolean handleCustomTrigger(Class<? extends InteractionWiredTrigger> triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (!Emulator.isReady)
            return false;

        if (room == null)
            return false;

        if (RoomWiredDisableSupport.isWiredDisabled(room))
            return false;

        if (!room.isLoaded())
            return false;

        if (room.getRoomSpecialTypes() == null)
            return false;

        THashSet<InteractionWiredTrigger> triggers = room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CUSTOM);

        if (triggers == null || triggers.isEmpty())
            return false;

        long millis = System.currentTimeMillis();
        List<LegacyExecutionPlan> executionPlans = new ArrayList<>();

        LinkedHashSet<Long> triggeredTiles = new LinkedHashSet<>();
        for (InteractionWiredTrigger trigger : triggers) {
            if (trigger.getClass() != triggerType) continue;

            long coordinateKey = toTileCoordinateKey(trigger.getX(), trigger.getY());

            if (!triggeredTiles.add(coordinateKey))
                continue;

            LegacyExecutionPlan executionPlan = new LegacyExecutionPlan();

            if (handle(trigger, roomUnit, room, stuff, executionPlan)) {
                executionPlans.add(executionPlan);
            }
        }

        for (LegacyExecutionPlan executionPlan : executionPlans) {
            triggerEffects(executionPlan.effects, roomUnit, room, stuff, millis, executionPlan.executeInOrder);
        }

        return !executionPlans.isEmpty();
    }

    public static boolean handle(InteractionWiredTrigger trigger, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        long millis = System.currentTimeMillis();
        LegacyExecutionPlan executionPlan = new LegacyExecutionPlan();

        if (RoomWiredDisableSupport.isWiredDisabled(room))
            return false;

        if(handle(trigger, roomUnit, room, stuff, executionPlan)) {
            triggerEffects(executionPlan.effects, roomUnit, room, stuff, millis, executionPlan.executeInOrder);
            return true;
        }
        return false;
    }

    private static boolean handle(InteractionWiredTrigger trigger, final RoomUnit roomUnit, final Room room, final Object[] stuff, final LegacyExecutionPlan executionPlan) {
        long millis = System.currentTimeMillis();
        int roomUnitId = roomUnit != null ? roomUnit.getId() : -1;

        // Only one thread may process a given trigger box at a time, so the
        // cooldown check (below) and setCooldown (further down) act as one
        // atomic claim — preventing a concurrent packet/cycle double-fire.
        if (!trigger.tryBeginProcessing()) {
            return false;
        }

        try {
        if (Emulator.isReady && ((Emulator.getConfig().getBoolean("wired.custom.enabled", false) && (trigger.canExecute(millis) || roomUnitId > -1) && trigger.userCanExecute(roomUnitId, millis)) || (!Emulator.getConfig().getBoolean("wired.custom.enabled", false) && trigger.canExecute(millis))) && trigger.execute(roomUnit, room, stuff)) {
            THashSet<InteractionWiredCondition> conditions = room.getRoomSpecialTypes().getConditions(trigger.getX(), trigger.getY());
            THashSet<InteractionWiredEffect> effects = room.getRoomSpecialTypes().getEffects(trigger.getX(), trigger.getY());
            THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY());
            WiredExtraExecutionLimit executionLimitExtra = null;
            WiredExtraRandom randomExtra = null;

            int conditionEvaluationMode = WiredExtraOrEval.MODE_ALL;
            int conditionEvaluationValue = 1;
            boolean hasExtraUnseen = false;
            boolean hasExtraExecuteInOrder = false;

            for (InteractionWiredExtra extra : extras) {
                if (executionLimitExtra == null && extra instanceof WiredExtraExecutionLimit) {
                    executionLimitExtra = (WiredExtraExecutionLimit) extra;
                }

                if (randomExtra == null && extra instanceof WiredExtraRandom) {
                    randomExtra = (WiredExtraRandom) extra;
                }

                if (!hasExtraUnseen && extra instanceof WiredExtraUnseen) {
                    hasExtraUnseen = true;
                }

                if (!hasExtraExecuteInOrder && extra instanceof WiredExtraExecuteInOrder) {
                    hasExtraExecuteInOrder = true;
                }

                if (extra instanceof WiredExtraOrEval) {
                    conditionEvaluationMode = ((WiredExtraOrEval) extra).getEvaluationMode();
                    conditionEvaluationValue = ((WiredExtraOrEval) extra).getCompareValue();
                }
            }

            if (!conditions.isEmpty()) {

                if (!evaluateConditions(conditions, roomUnit, room, stuff, conditionEvaluationMode, conditionEvaluationValue)) {
                    for (InteractionWiredCondition condition : conditions) {
                        if (!Emulator.getPluginManager().fireEvent(new WiredConditionFailedEvent(room, roomUnit, trigger, condition)).isCancelled()) {
                            break;
                        }
                    }

                    return false;
                }
            }

            if (executionLimitExtra != null && !executionLimitExtra.tryAcquireExecutionSlot(millis)) {
                return false;
            }

            if (Emulator.getPluginManager().fireEvent(new WiredStackTriggeredEvent(room, roomUnit, trigger, effects, conditions)).isCancelled())
                return false;

            trigger.activateBox(room, roomUnit, millis);

            trigger.setCooldown(millis);

            for (InteractionWiredExtra extra : extras) {
                extra.activateBox(room, roomUnit, millis);
            }

            List<InteractionWiredEffect> effectList = (hasExtraUnseen || hasExtraExecuteInOrder)
                    ? WiredExecutionOrderUtil.sort(effects)
                    : new ArrayList<>(effects);

            executionPlan.executeInOrder = hasExtraExecuteInOrder;

            if (hasExtraUnseen) {
                for (InteractionWiredExtra extra : extras) {
                    if (extra instanceof WiredExtraUnseen) {
                        extra.setExtradata(extra.getExtradata().equals("1") ? "0" : "1");
                        InteractionWiredEffect effect = ((WiredExtraUnseen) extra).getUnseenEffect(effectList);
                        if (effect != null) {
                            executionPlan.effects.add(effect);
                        }
                        break;
                    }
                }
            } else if (randomExtra != null) {
                executionPlan.effects.addAll(randomExtra.selectEffects(effectList));
            } else if (hasExtraExecuteInOrder) {
                executionPlan.effects.addAll(effectList);
            } else {
                for (final InteractionWiredEffect effect : effectList) {
                    executionPlan.effects.add(effect);
                }
            }

            return !Emulator.getPluginManager().fireEvent(new WiredStackExecutedEvent(room, roomUnit, trigger, effects, conditions)).isCancelled();
        }

        return false;
        } finally {
            trigger.endProcessing();
        }
    }

    private static boolean evaluateConditions(THashSet<InteractionWiredCondition> conditions, RoomUnit roomUnit, Room room, Object[] stuff, int evaluationMode, int evaluationValue) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        Map<WiredConditionType, Boolean> orGroupResults = new HashMap<>();
        int matchedRequirements = 0;
        int totalRequirements = 0;

        for (InteractionWiredCondition condition : conditions) {
            boolean result = condition.execute(roomUnit, room, stuff);

            if (condition.operator() == WiredConditionOperator.OR) {
                orGroupResults.merge(condition.getType(), result, (left, right) -> left || right);
                continue;
            }

            totalRequirements++;
            if (result) {
                matchedRequirements++;
            }
        }

        totalRequirements += orGroupResults.size();

        for (Boolean groupResult : orGroupResults.values()) {
            if (Boolean.TRUE.equals(groupResult)) {
                matchedRequirements++;
            }
        }

        return WiredExtraOrEval.matchesMode(evaluationMode, matchedRequirements, totalRequirements, evaluationValue);
    }

    private static boolean triggerEffect(InteractionWiredEffect effect, RoomUnit roomUnit, Room room, Object[] stuff, long millis) {
        boolean executed = false;
        if (effect != null && (effect.canExecute(millis) || (roomUnit != null && effect.requiresTriggeringUser() && Emulator.getConfig().getBoolean("wired.custom.enabled", false) && effect.userCanExecute(roomUnit.getId(), millis)))) {
            executed = true;
            if (!effect.requiresTriggeringUser() || (roomUnit != null && effect.requiresTriggeringUser())) {
                Runnable execution = () -> {
                    if (room.isLoaded() && room.getHabbos().size() > 0) {
                        try {
                            if (!effect.execute(roomUnit, room, stuff)) return;
                            effect.setCooldown(millis);
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }

                        effect.activateBox(room, roomUnit, millis);
                    }
                };

                long delayMs = effect.getDelay() * 500L;
                long elapsedSinceTrigger = Math.max(0L, System.currentTimeMillis() - millis);
                long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);

                if (delayMs <= 0) {
                    execution.run();
                } else {
                    Emulator.getThreading().run(execution, remainingDelayMs);
                }
            }
        }

        return executed;
    }

    private static void triggerEffects(LinkedHashSet<InteractionWiredEffect> effects, RoomUnit roomUnit, Room room, Object[] stuff, long millis, boolean executeInOrder) {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        if (!executeInOrder) {
            for (InteractionWiredEffect effect : effects) {
                triggerEffect(effect, roomUnit, room, stuff, millis);
            }
            return;
        }

        LinkedHashSet<InteractionWiredEffect> queueableEffects = new LinkedHashSet<>();

        for (InteractionWiredEffect effect : effects) {
            if (canQueueEffect(effect, roomUnit, millis)) {
                queueableEffects.add(effect);
            }
        }

        Map<Integer, List<InteractionWiredEffect>> delayBatches = new LinkedHashMap<>();
        for (InteractionWiredEffect effect : queueableEffects) {
            delayBatches.computeIfAbsent(effect.getDelay(), ignored -> new ArrayList<>()).add(effect);
        }

        for (Map.Entry<Integer, List<InteractionWiredEffect>> entry : delayBatches.entrySet()) {
            Integer delay = entry.getKey();
            List<InteractionWiredEffect> delayBatch = entry.getValue();
            if (delayBatch.isEmpty()) {
                continue;
            }

            if (delay > 0) {
                long delayMs = delay * 500L;
                long elapsedSinceTrigger = Math.max(0L, System.currentTimeMillis() - millis);
                long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);
                Emulator.getThreading().run(() -> executeOrderedEffectBatch(delayBatch, roomUnit, room, stuff, millis), remainingDelayMs);
            } else {
                executeOrderedEffectBatch(delayBatch, roomUnit, room, stuff, millis);
            }
        }
    }

    private static boolean canQueueEffect(InteractionWiredEffect effect, RoomUnit roomUnit, long millis) {
        if (effect == null) {
            return false;
        }

        boolean canExecute = effect.canExecute(millis)
                || (roomUnit != null && effect.requiresTriggeringUser()
                && Emulator.getConfig().getBoolean("wired.custom.enabled", false)
                && effect.userCanExecute(roomUnit.getId(), millis));

        if (!canExecute) {
            return false;
        }

        return !effect.requiresTriggeringUser() || roomUnit != null;
    }

    private static void executeOrderedEffectBatch(List<InteractionWiredEffect> effects, RoomUnit roomUnit, Room room, Object[] stuff, long millis) {
        if (!room.isLoaded() || room.getHabbos().size() <= 0) {
            return;
        }

        for (InteractionWiredEffect effect : effects) {
            try {
                if (!effect.execute(roomUnit, room, stuff)) {
                    continue;
                }

                effect.setCooldown(millis);
                effect.activateBox(room, roomUnit, millis);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    public static GsonBuilder getGsonBuilder() {
        if(gsonBuilder == null) {
            synchronized (WiredHandler.class) {
                if (gsonBuilder == null) {
                    gsonBuilder = new GsonBuilder();
                }
            }
        }
        return gsonBuilder;
    }

    private static long toTileCoordinateKey(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    public static boolean executeEffectsAtTiles(THashSet<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        for (RoomTile tile : tiles) {
            if (room != null) {
                THashSet<HabboItem> items = room.getItemsAt(tile);

                long millis = room.getCycleTimestamp();
                for (final HabboItem item : items) {
                    if (item instanceof InteractionWiredEffect && !(item instanceof WiredEffectTriggerStacks)) {
                        triggerEffect((InteractionWiredEffect) item, roomUnit, room, stuff, millis);
                        ((InteractionWiredEffect) item).setCooldown(millis);
                    }
                }
            }
        }

        return true;
    }

    public static void dropRewards(int wiredId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_rewards_given WHERE wired_item = ?")) {
            statement.setInt(1, wiredId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static void persistReward(int wiredId, int habboId, int rewardId, int timestamp) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO wired_rewards_given (wired_item, user_id, reward_id, timestamp) VALUES ( ?, ?, ?, ?)")) {
            statement.setInt(1, wiredId);
            statement.setInt(2, habboId);
            statement.setInt(3, rewardId);
            statement.setInt(4, timestamp);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static void completeReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward, int successCode) {
        if (wiredBox.limit > 0)
            wiredBox.incrementGiven();

        persistReward(wiredBox.getId(), habbo.getHabboInfo().getId(), reward.id, Emulator.getIntUnixTimestamp());
        habbo.getClient().sendResponse(new WiredRewardAlertComposer(successCode));
    }

    private static boolean giveReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward) {
        if (reward.badge) {
            UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, "badge", reward.data);
            if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
                return false;

            if (rewardReceived.value.isEmpty())
                return false;
            
            if (habbo.getInventory().getBadgesComponent().hasBadge(rewardReceived.value)) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED));
                return false;
            }

            HabboBadge badge = new HabboBadge(0, rewardReceived.value, 0, habbo);
            Emulator.getThreading().run(badge);
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_BADGE);
            return true;
        }

        String[] data = reward.data.split("#");

        if (data.length != 2)
            return false;

        UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, data[0], data[1]);
        if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
            return false;

        String rewardType = rewardReceived.type == null ? "" : rewardReceived.type.trim();
        String rewardValue = rewardReceived.value == null ? "" : rewardReceived.value.trim();

        if (rewardValue.isEmpty())
            return false;

        if (rewardType.equalsIgnoreCase("credits")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.giveCredits(amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.equalsIgnoreCase("diamonds") || rewardType.equalsIgnoreCase("diamond")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.givePoints(5, amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.equalsIgnoreCase("pixels")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.givePixels(amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.startsWith("points")) {
            Integer points = parsePositiveRewardInteger(rewardValue);
            if (points == null) return false;

            int type = 5;

            try {
                int parsedType = Integer.parseInt(rewardType.replace("points", "").trim());
                if (parsedType > 0) {
                    type = parsedType;
                }
            } catch (NumberFormatException ignored) {
            }

            habbo.givePoints(type, points);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.equalsIgnoreCase("furni")) {
            Integer itemId = parsePositiveRewardInteger(rewardValue);
            if (itemId == null) return false;

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);
            if (baseItem == null)
                return false;

            HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
            if (item == null)
                return false;

            habbo.getClient().sendResponse(new AddHabboItemComposer(item));
            habbo.getClient().getHabbo().getInventory().getItemsComponent().addItem(item);
            habbo.getClient().sendResponse(new PurchaseOKComposer(null));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.equalsIgnoreCase("respect")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.getHabboStats().respectPointsReceived += amount;
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        } else if (rewardType.equalsIgnoreCase("cata")) {
            Integer catalogItemId = parsePositiveRewardInteger(rewardValue);
            if (catalogItemId == null) return false;

            CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogItemId);

            if (item == null)
                return false;

            Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, habbo, 1, "", true);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        return false;
    }

    private static Integer parsePositiveRewardInteger(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean getReward(Habbo habbo, WiredEffectGiveReward wiredBox) {
        if (wiredBox.limit > 0) {
            if (wiredBox.limit - wiredBox.getGiven() == 0) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.LIMITED_NO_MORE_AVAILABLE));
                return false;
            }
        }

        WiredGiveRewardItem rewardToGive = null;
        int failureCode = -1;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM wired_rewards_given WHERE user_id = ? AND wired_item = ? ORDER BY timestamp DESC LIMIT ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, wiredBox.getId());
            statement.setInt(3, wiredBox.rewardItems.size());

            try (ResultSet set = statement.executeQuery()) {
                if (set.first()) {
                    set.last();
                    int rowCount = set.getRow();
                    set.first();

                    if (rowCount >= 1) {
                        if (wiredBox.rewardTime == WiredEffectGiveReward.LIMIT_ONCE) {
                            failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED;
                        }
                    }

                    if (failureCode == -1) {
                        if (wiredBox.rewardTime == WiredEffectGiveReward.LIMIT_N_MINUTES) {
                            if (Emulator.getIntUnixTimestamp() - set.getInt("timestamp") <= 60) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_MINUTE;
                            }
                        }

                        if (failureCode == -1 && wiredBox.uniqueRewards) {
                            if (rowCount == wiredBox.rewardItems.size()) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                            }
                        }

                        if (failureCode == -1 && wiredBox.rewardTime == WiredEffectGiveReward.LIMIT_N_HOURS) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (3600 * wiredBox.limitationInterval))) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_HOUR;
                            }
                        }

                        if (failureCode == -1 && wiredBox.rewardTime == WiredEffectGiveReward.LIMIT_N_DAY) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (86400 * wiredBox.limitationInterval))) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_TODAY;
                            }
                        }
                    }

                    if (failureCode == -1) {
                        if (wiredBox.uniqueRewards) {
                            for (WiredGiveRewardItem item : wiredBox.rewardItems) {
                                set.beforeFirst();
                                boolean found = false;

                                while (set.next()) {
                                    if (set.getInt("reward_id") == item.id)
                                        found = true;
                                }

                                if (!found) {
                                    rewardToGive = item;
                                    break;
                                }
                            }

                            if (rewardToGive == null) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }

        if (failureCode != -1) {
            habbo.getClient().sendResponse(new WiredRewardAlertComposer(failureCode));
            return false;
        }

        // If no unique reward was determined and there are no failures, pick a random reward or the first unique one
        if (rewardToGive == null) {
            if (wiredBox.uniqueRewards) {
                if (!wiredBox.rewardItems.isEmpty()) {
                    rewardToGive = wiredBox.rewardItems.get(0);
                } else {
                    failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                }
            } else {
                int randomNumber = Emulator.getRandom().nextInt(101);
                int count = 0;
                for (WiredGiveRewardItem item : wiredBox.rewardItems) {
                    if (randomNumber >= count && randomNumber <= (count + item.probability)) {
                        rewardToGive = item;
                        break;
                    }
                    count += item.probability;
                }

                if (rewardToGive == null) {
                    failureCode = WiredRewardAlertComposer.UNLUCKY_NO_REWARD;
                }
            }
        }

        if (failureCode != -1) {
            habbo.getClient().sendResponse(new WiredRewardAlertComposer(failureCode));
            return false;
        }

        if (rewardToGive != null) {
            return giveReward(habbo, wiredBox, rewardToGive);
        }

        return false;
    }

    public static void resetTimers(Room room) {
        if (!room.isLoaded() || room.getRoomSpecialTypes() == null)
            return;

        room.getRoomSpecialTypes().getTriggers().forEach(t -> {
            if (t == null) return;
            
            if (t.getType() == WiredTriggerType.AT_GIVEN_TIME || t.getType() == WiredTriggerType.PERIODICALLY || t.getType() == WiredTriggerType.PERIODICALLY_LONG || t.getType() == WiredTriggerType.PERIODICALLY_SHORT) {
                ((WiredTriggerReset) t).resetTimer();
            }
        });

        room.setLastTimerReset(Emulator.getIntUnixTimestamp());
    }
}
