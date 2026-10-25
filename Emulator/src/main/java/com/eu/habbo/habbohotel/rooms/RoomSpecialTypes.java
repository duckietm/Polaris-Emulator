package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.InteractionTent;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGate;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGate;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboard;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTree;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages special room item types including wired components, game elements, and pets.
 * This class is thread-safe and uses concurrent collections for wired components
 * to support high-frequency access patterns.
 */
public class RoomSpecialTypes {
    private final Map<Integer, InteractionBattleBanzaiTeleporter> banzaiTeleporters;
    private final Map<Integer, InteractionNest> nests;
    private final Map<Integer, InteractionPetDrink> petDrinks;
    private final Map<Integer, InteractionPetFood> petFoods;
    private final Map<Integer, InteractionPetToy> petToys;
    private final Map<Integer, InteractionPetTree> petTrees;
    private final Map<Integer, InteractionRoller> rollers;

    // The wired boxes of the room, each kind found by item id, by type and by tile
    private final WiredComponentIndex<InteractionWiredTrigger, WiredTriggerType> triggers =
            new WiredComponentIndex<>(InteractionWiredTrigger::getType);
    private final WiredComponentIndex<InteractionWiredEffect, WiredEffectType> effects =
            new WiredComponentIndex<>(InteractionWiredEffect::getType);
    private final WiredComponentIndex<InteractionWiredCondition, WiredConditionType> conditions =
            new WiredComponentIndex<>(InteractionWiredCondition::getType);
    private final WiredComponentIndex<InteractionWiredExtra, Void> extras = WiredComponentIndex.byTileOnly();

    private final Map<Integer, InteractionGameScoreboard> gameScoreboards;
    private final Map<Integer, InteractionGameGate> gameGates;
    private final Map<Integer, InteractionGameTimer> gameTimers;

    private final Map<Integer, InteractionFreezeExitTile> freezeExitTile;
    private final Map<Integer, HabboItem> undefined;
    private final Set<ICycleable> cycleTasks;
    private final ConcurrentHashMap<Integer, HabboItem> specialItemsById = new ConcurrentHashMap<>();

    public RoomSpecialTypes() {
        // Concurrent maps: game cycles, wired and packet threads read and change these at once, and
        // several paths iterated a HashMap without its lock (ConcurrentModificationException or a
        // corrupted map mid-game).
        this.banzaiTeleporters = new ConcurrentHashMap<>();
        this.nests = new ConcurrentHashMap<>();
        this.petDrinks = new ConcurrentHashMap<>();
        this.petFoods = new ConcurrentHashMap<>();
        this.petToys = new ConcurrentHashMap<>();
        this.petTrees = new ConcurrentHashMap<>();
        // Rollers stay a HashMap: plugins rely on the live map accepting null values, and every
        // first-party read and change of it already holds its monitor (see rollerSnapshot).
        this.rollers = new HashMap<>(0);

        this.gameScoreboards = new ConcurrentHashMap<>();
        this.gameGates = new ConcurrentHashMap<>();
        this.gameTimers = new ConcurrentHashMap<>();

        this.freezeExitTile = new ConcurrentHashMap<>();
        this.undefined = new ConcurrentHashMap<>();
        this.cycleTasks = ConcurrentHashMap.newKeySet();
    }

    public InteractionBattleBanzaiTeleporter getBanzaiTeleporter(int itemId) {
        return this.banzaiTeleporters.get(itemId);
    }

    public void addBanzaiTeleporter(InteractionBattleBanzaiTeleporter item) {
        this.banzaiTeleporters.put(item.getId(), item);
        this.specialItemsById.put(item.getId(), item);
    }

    public void removeBanzaiTeleporter(InteractionBattleBanzaiTeleporter item) {
        this.banzaiTeleporters.remove(item.getId());
        this.specialItemsById.remove(item.getId());
    }

    public Set<InteractionBattleBanzaiTeleporter> getBanzaiTeleporters() {
        synchronized (this.banzaiTeleporters) {
            Set<InteractionBattleBanzaiTeleporter> battleBanzaiTeleporters = new LinkedHashSet<>();
            battleBanzaiTeleporters.addAll(this.banzaiTeleporters.values());

            return battleBanzaiTeleporters;
        }
    }

    public InteractionBattleBanzaiTeleporter getRandomTeleporter(
            Item baseItem, InteractionBattleBanzaiTeleporter exclude) {
        List<InteractionBattleBanzaiTeleporter> teleporterList = new ArrayList<>();
        for (InteractionBattleBanzaiTeleporter teleporter : this.banzaiTeleporters.values()) {
            if (baseItem == null || teleporter.getBaseItem() == baseItem) {
                teleporterList.add(teleporter);
            }
        }

        teleporterList.remove(exclude);

        if (!teleporterList.isEmpty()) {
            Collections.shuffle(teleporterList);
            return teleporterList.get(0);
        }

        return null;
    }

    public InteractionNest getNest(int itemId) {
        synchronized (this.nests) {
            return this.nests.get(itemId);
        }
    }

    public void addNest(InteractionNest item) {
        synchronized (this.nests) {
            this.nests.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removeNest(InteractionNest item) {
        synchronized (this.nests) {
            this.nests.remove(item.getId());
        }
        this.specialItemsById.remove(item.getId());
    }

    public Set<InteractionNest> getNests() {
        synchronized (this.nests) {
            Set<InteractionNest> nests = new LinkedHashSet<>();
            nests.addAll(this.nests.values());

            return nests;
        }
    }

    public InteractionPetDrink getPetDrink(int itemId) {
        synchronized (this.petDrinks) {
            return this.petDrinks.get(itemId);
        }
    }

    public void addPetDrink(InteractionPetDrink item) {
        synchronized (this.petDrinks) {
            this.petDrinks.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removePetDrink(InteractionPetDrink item) {
        synchronized (this.petDrinks) {
            this.petDrinks.remove(item.getId());
        }
        this.specialItemsById.remove(item.getId());
    }

    public Set<InteractionPetDrink> getPetDrinks() {
        synchronized (this.petDrinks) {
            Set<InteractionPetDrink> petDrinks = new LinkedHashSet<>();
            petDrinks.addAll(this.petDrinks.values());

            return petDrinks;
        }
    }

    public InteractionPetFood getPetFood(int itemId) {
        synchronized (this.petFoods) {
            return this.petFoods.get(itemId);
        }
    }

    public void addPetFood(InteractionPetFood item) {
        synchronized (this.petFoods) {
            this.petFoods.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removePetFood(InteractionPetFood petFood) {
        synchronized (this.petFoods) {
            this.petFoods.remove(petFood.getId());
        }
        this.specialItemsById.remove(petFood.getId());
    }

    public Set<InteractionPetFood> getPetFoods() {
        synchronized (this.petFoods) {
            Set<InteractionPetFood> petFoods = new LinkedHashSet<>();
            petFoods.addAll(this.petFoods.values());

            return petFoods;
        }
    }

    public InteractionPetToy getPetToy(int itemId) {
        synchronized (this.petToys) {
            return this.petToys.get(itemId);
        }
    }

    public void addPetToy(InteractionPetToy item) {
        synchronized (this.petToys) {
            this.petToys.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removePetToy(InteractionPetToy petToy) {
        synchronized (this.petToys) {
            this.petToys.remove(petToy.getId());
        }
        this.specialItemsById.remove(petToy.getId());
    }

    public Set<InteractionPetToy> getPetToys() {
        synchronized (this.petToys) {
            Set<InteractionPetToy> petToys = new LinkedHashSet<>();
            petToys.addAll(this.petToys.values());

            return petToys;
        }
    }

    public InteractionPetTree getPetTree(int itemId) {
        synchronized (this.petTrees) {
            return this.petTrees.get(itemId);
        }
    }

    public void addPetTree(InteractionPetTree item) {
        synchronized (this.petTrees) {
            this.petTrees.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removePetTree(InteractionPetTree petTree) {
        synchronized (this.petTrees) {
            this.petTrees.remove(petTree.getId());
        }
        this.specialItemsById.remove(petTree.getId());
    }

    public Set<InteractionPetTree> getPetTrees() {
        synchronized (this.petTrees) {
            Set<InteractionPetTree> petTrees = new LinkedHashSet<>();
            petTrees.addAll(this.petTrees.values());

            return petTrees;
        }
    }

    public InteractionRoller getRoller(int itemId) {
        synchronized (this.rollers) {
            return this.rollers.get(itemId);
        }
    }

    public void addRoller(InteractionRoller item) {
        synchronized (this.rollers) {
            this.rollers.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removeRoller(InteractionRoller roller) {
        synchronized (this.rollers) {
            this.rollers.remove(roller.getId());
        }
        this.specialItemsById.remove(roller.getId());
    }

    public Map<Integer, InteractionRoller> getRollers() {
        return this.rollers;
    }

    Map<Integer, InteractionRoller> rollerSnapshot() {
        synchronized (this.rollers) {
            return new HashMap<>(this.rollers);
        }
    }

    /**
     * Finds a wired trigger by its item ID.
     * @param itemId The item ID to search for
     * @return The trigger if found, null otherwise
     */
    public InteractionWiredTrigger getTrigger(int itemId) {
        return this.triggers.find(itemId);
    }

    /**
     * Gets all wired triggers in the room.
     * @return A new set containing all triggers (safe for iteration)
     */
    public Set<InteractionWiredTrigger> getTriggers() {
        return this.triggers.all();
    }

    /**
     * Gets all wired triggers of a specific type.
     * @param type The trigger type to filter by
     * @return A new set containing matching triggers (safe for iteration)
     */
    public Set<InteractionWiredTrigger> getTriggers(WiredTriggerType type) {
        return this.triggers.ofType(type);
    }

    /**
     * Gets all wired triggers at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing triggers at the location (safe for iteration)
     */
    public Set<InteractionWiredTrigger> getTriggers(int x, int y) {
        return this.triggers.at(x, y);
    }

    /**
     * Adds a wired trigger to the room.
     * @param trigger The trigger to add
     */
    public static final int MAX_SIGNAL_SENDERS_PER_ROOM = 0;

    public static final int MAX_SIGNAL_RECEIVERS_PER_ROOM = 0;
    public static final int MAX_SENDERS_PER_RECEIVER = 0;

    public boolean isSignalSenderLimitReached() {
        return false;
    }

    public boolean isSignalReceiverLimitReached() {
        return false;
    }

    public int countSendersTargetingReceiver(int receiverItemId, InteractionWiredEffect excludeSender) {
        Set<InteractionWiredEffect> senders = this.getSignalSenders();
        if (senders == null) return 0;

        int count = 0;
        for (InteractionWiredEffect effect : senders) {
            if (excludeSender != null && effect.getId() == excludeSender.getId()) continue;
            if (effect instanceof WiredEffectSendSignal) {
                WiredEffectSendSignal sender = (WiredEffectSendSignal) effect;
                if (sender.hasPickedItem(receiverItemId)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countSendersTargetingReceiver(int receiverItemId) {
        return countSendersTargetingReceiver(receiverItemId, null);
    }

    public int countSendersTargetingAnyReceiver(
            Collection<Integer> receiverItemIds, InteractionWiredEffect excludeSender) {
        if (receiverItemIds == null || receiverItemIds.isEmpty()) {
            return 0;
        }

        Set<InteractionWiredEffect> senders = this.getSignalSenders();
        if (senders == null) {
            return 0;
        }

        Set<Integer> uniqueSenderIds = new HashSet<>();

        for (InteractionWiredEffect effect : senders) {
            if (excludeSender != null && effect.getId() == excludeSender.getId()) continue;
            if (!(effect instanceof WiredEffectSendSignal)) continue;

            WiredEffectSendSignal sender = (WiredEffectSendSignal) effect;
            for (Integer receiverItemId : receiverItemIds) {
                if (receiverItemId == null) continue;
                if (!sender.hasPickedItem(receiverItemId)) continue;

                uniqueSenderIds.add(effect.getId());
                break;
            }
        }

        return uniqueSenderIds.size();
    }

    public int countSendersTargetingAnyReceiver(Collection<Integer> receiverItemIds) {
        return countSendersTargetingAnyReceiver(receiverItemIds, null);
    }

    public boolean unlinkSignalAntennaReferences(int antennaItemId) {
        if (antennaItemId <= 0) {
            return false;
        }

        boolean changed = false;

        Set<InteractionWiredTrigger> receivers = this.getTriggers(WiredTriggerType.RECEIVE_SIGNAL);
        for (InteractionWiredTrigger trigger : receivers) {
            if (!(trigger
                    instanceof
                    com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal receiver)) {
                continue;
            }

            if (!receiver.unlinkAntenna(antennaItemId)) {
                continue;
            }

            changed = true;
            Emulator.getThreading().run(receiver);
        }

        Set<InteractionWiredEffect> senders = this.getSignalSenders();
        if (senders != null) {
            for (InteractionWiredEffect effect : senders) {
                if (!(effect instanceof WiredEffectSendSignal sender)) {
                    continue;
                }

                if (!sender.unlinkAntenna(antennaItemId)) {
                    continue;
                }

                changed = true;
                Emulator.getThreading().run(sender);
            }
        }

        return changed;
    }

    private Set<InteractionWiredEffect> getSignalSenders() {
        Set<InteractionWiredEffect> senders = new HashSet<>();

        Set<InteractionWiredEffect> standardSenders = this.effects.liveOfType(WiredEffectType.SEND_SIGNAL);
        if (standardSenders != null) {
            senders.addAll(standardSenders);
        }

        Set<InteractionWiredEffect> negativeSenders = this.effects.liveOfType(WiredEffectType.NEG_SEND_SIGNAL);
        if (negativeSenders != null) {
            senders.addAll(negativeSenders);
        }

        return senders.isEmpty() ? null : senders;
    }

    public void addTrigger(InteractionWiredTrigger trigger) {
        this.triggers.add(trigger);
        this.specialItemsById.put(trigger.getId(), trigger);
    }

    /**
     * Removes a wired trigger from the room.
     * @param trigger The trigger to remove
     */
    public void removeTrigger(InteractionWiredTrigger trigger) {
        this.triggers.remove(trigger);
        this.specialItemsById.remove(trigger.getId());
    }

    /**
     * Updates the spatial index when a trigger is moved.
     * @param trigger The trigger that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateTriggerLocation(InteractionWiredTrigger trigger, int oldX, int oldY) {
        this.triggers.moved(trigger, oldX, oldY);
    }

    /**
     * Finds a wired effect by its item ID.
     * @param itemId The item ID to search for
     * @return The effect if found, null otherwise
     */
    public InteractionWiredEffect getEffect(int itemId) {
        return this.effects.find(itemId);
    }

    /**
     * Gets all wired effects in the room.
     * @return A new set containing all effects (safe for iteration)
     */
    public Set<InteractionWiredEffect> getEffects() {
        return this.effects.all();
    }

    /**
     * Gets all wired effects of a specific type.
     * @param type The effect type to filter by
     * @return A new set containing matching effects (safe for iteration)
     */
    public Set<InteractionWiredEffect> getEffects(WiredEffectType type) {
        return this.effects.ofType(type);
    }

    /**
     * Gets all wired effects at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing effects at the location (safe for iteration)
     */
    public Set<InteractionWiredEffect> getEffects(int x, int y) {
        return this.effects.at(x, y);
    }

    /**
     * Adds a wired effect to the room.
     * @param effect The effect to add
     */
    public void addEffect(InteractionWiredEffect effect) {
        this.effects.add(effect);
        this.specialItemsById.put(effect.getId(), effect);
    }

    /**
     * Removes a wired effect from the room.
     * @param effect The effect to remove
     */
    public void removeEffect(InteractionWiredEffect effect) {
        this.effects.remove(effect);
        this.specialItemsById.remove(effect.getId());
    }

    /**
     * Updates the spatial index when an effect is moved.
     * @param effect The effect that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateEffectLocation(InteractionWiredEffect effect, int oldX, int oldY) {
        this.effects.moved(effect, oldX, oldY);
    }

    /**
     * Finds a wired condition by its item ID.
     * @param itemId The item ID to search for
     * @return The condition if found, null otherwise
     */
    public InteractionWiredCondition getCondition(int itemId) {
        return this.conditions.find(itemId);
    }

    /**
     * Gets all wired conditions in the room.
     * @return A new set containing all conditions (safe for iteration)
     */
    public Set<InteractionWiredCondition> getConditions() {
        return this.conditions.all();
    }

    /**
     * Gets all wired conditions of a specific type.
     * @param type The condition type to filter by
     * @return A new set containing matching conditions (safe for iteration)
     */
    public Set<InteractionWiredCondition> getConditions(WiredConditionType type) {
        return this.conditions.ofType(type);
    }

    /**
     * Gets all wired conditions at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing conditions at the location (safe for iteration)
     */
    public Set<InteractionWiredCondition> getConditions(int x, int y) {
        return this.conditions.at(x, y);
    }

    /**
     * Adds a wired condition to the room.
     * @param condition The condition to add
     */
    public void addCondition(InteractionWiredCondition condition) {
        this.conditions.add(condition);
        this.specialItemsById.put(condition.getId(), condition);
    }

    /**
     * Removes a wired condition from the room.
     * @param condition The condition to remove
     */
    public void removeCondition(InteractionWiredCondition condition) {
        this.conditions.remove(condition);
        this.specialItemsById.remove(condition.getId());
    }

    /**
     * Updates the spatial index when a condition is moved.
     * @param condition The condition that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateConditionLocation(InteractionWiredCondition condition, int oldX, int oldY) {
        this.conditions.moved(condition, oldX, oldY);
    }

    /**
     * Gets all wired extras in the room.
     * @return A new set containing all extras (safe for iteration)
     */
    public Set<InteractionWiredExtra> getExtras() {
        return this.extras.all();
    }

    /**
     * Finds a wired extra by its item ID.
     * @param itemId The item ID to search for
     * @return The extra if found, null otherwise
     */
    public InteractionWiredExtra getExtra(int itemId) {
        return this.extras.find(itemId);
    }

    /**
     * Gets all wired extras at specific coordinates using spatial index.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return A new set containing extras at the location (safe for iteration)
     */
    public Set<InteractionWiredExtra> getExtras(int x, int y) {
        return this.extras.at(x, y);
    }

    /**
     * Adds a wired extra to the room.
     * @param extra The extra to add
     */
    public void addExtra(InteractionWiredExtra extra) {
        this.extras.add(extra);
        this.specialItemsById.put(extra.getId(), extra);
    }

    /**
     * Removes a wired extra from the room.
     * @param extra The extra to remove
     */
    public void removeExtra(InteractionWiredExtra extra) {
        this.extras.remove(extra);
        this.specialItemsById.remove(extra.getId());
    }

    /**
     * Updates the spatial index when an extra is moved.
     * @param extra The extra that was moved
     * @param oldX The old X coordinate
     * @param oldY The old Y coordinate
     */
    public void updateExtraLocation(InteractionWiredExtra extra, int oldX, int oldY) {
        this.extras.moved(extra, oldX, oldY);
    }

    /**
     * Checks if there's a wired extra of a specific type at given coordinates.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param type The extra type to check for
     * @return true if an extra of the given type exists at the location
     */
    public boolean hasExtraType(short x, short y, Class<? extends InteractionWiredExtra> type) {
        Set<InteractionWiredExtra> onTile = this.extras.liveAt(x, y);
        if (onTile == null) {
            return false;
        }
        for (InteractionWiredExtra extra : onTile) {
            if (type.isAssignableFrom(extra.getClass())) {
                return true;
            }
        }
        return false;
    }

    public InteractionGameScoreboard getGameScorebord(int itemId) {
        return this.gameScoreboards.get(itemId);
    }

    public void addGameScoreboard(InteractionGameScoreboard scoreboard) {
        this.gameScoreboards.put(scoreboard.getId(), scoreboard);
        this.specialItemsById.put(scoreboard.getId(), scoreboard);
    }

    public void removeScoreboard(InteractionGameScoreboard scoreboard) {
        this.gameScoreboards.remove(scoreboard.getId());
        this.specialItemsById.remove(scoreboard.getId());
    }

    public Map<Integer, InteractionFreezeScoreboard> getFreezeScoreboards() {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionFreezeScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionFreezeScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public Map<Integer, InteractionFreezeScoreboard> getFreezeScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionFreezeScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeScoreboard) {
                    if (((InteractionFreezeScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionFreezeScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public Map<Integer, InteractionBattleBanzaiScoreboard> getBattleBanzaiScoreboards() {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionBattleBanzaiScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionBattleBanzaiScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public Map<Integer, InteractionBattleBanzaiScoreboard> getBattleBanzaiScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionBattleBanzaiScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiScoreboard) {
                    if (((InteractionBattleBanzaiScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionBattleBanzaiScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public Map<Integer, InteractionFootballScoreboard> getFootballScoreboards() {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionFootballScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFootballScoreboard) {
                    boards.put(set.getValue().getId(), (InteractionFootballScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public Map<Integer, InteractionFootballScoreboard> getFootballScoreboards(GameTeamColors teamColor) {
        synchronized (this.gameScoreboards) {
            Map<Integer, InteractionFootballScoreboard> boards = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameScoreboard> set : this.gameScoreboards.entrySet()) {
                if (set.getValue() instanceof InteractionFootballScoreboard) {
                    if (((InteractionFootballScoreboard) set.getValue()).teamColor.equals(teamColor))
                        boards.put(set.getValue().getId(), (InteractionFootballScoreboard) set.getValue());
                }
            }

            return boards;
        }
    }

    public InteractionGameGate getGameGate(int itemId) {
        return this.gameGates.get(itemId);
    }

    public void addGameGate(InteractionGameGate gameGate) {
        this.gameGates.put(gameGate.getId(), gameGate);
        this.specialItemsById.put(gameGate.getId(), gameGate);
    }

    public void removeGameGate(InteractionGameGate gameGate) {
        this.gameGates.remove(gameGate.getId());
        this.specialItemsById.remove(gameGate.getId());
    }

    public Map<Integer, InteractionFreezeGate> getFreezeGates() {
        synchronized (this.gameGates) {
            Map<Integer, InteractionFreezeGate> gates = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameGate> set : this.gameGates.entrySet()) {
                if (set.getValue() instanceof InteractionFreezeGate) {
                    gates.put(set.getValue().getId(), (InteractionFreezeGate) set.getValue());
                }
            }

            return gates;
        }
    }

    public Map<Integer, InteractionBattleBanzaiGate> getBattleBanzaiGates() {
        synchronized (this.gameGates) {
            Map<Integer, InteractionBattleBanzaiGate> gates = new HashMap<>();

            for (Map.Entry<Integer, InteractionGameGate> set : this.gameGates.entrySet()) {
                if (set.getValue() instanceof InteractionBattleBanzaiGate) {
                    gates.put(set.getValue().getId(), (InteractionBattleBanzaiGate) set.getValue());
                }
            }

            return gates;
        }
    }

    public InteractionGameTimer getGameTimer(int itemId) {
        return this.gameTimers.get(itemId);
    }

    public void addGameTimer(InteractionGameTimer gameTimer) {
        this.gameTimers.put(gameTimer.getId(), gameTimer);
        this.specialItemsById.put(gameTimer.getId(), gameTimer);
    }

    public void removeGameTimer(InteractionGameTimer gameTimer) {
        this.gameTimers.remove(gameTimer.getId());
        this.specialItemsById.remove(gameTimer.getId());
    }

    public Map<Integer, InteractionGameTimer> getGameTimers() {
        return this.gameTimers;
    }

    public InteractionFreezeExitTile getFreezeExitTile() {
        return this.freezeExitTile.values().stream().findFirst().orElse(null);
    }

    public InteractionFreezeExitTile getRandomFreezeExitTile() {
        // One snapshot for both the pick and its bound: the size read separately could disagree with
        // the array, and an empty room threw from nextInt(0).
        Object[] tiles = this.freezeExitTile.values().toArray();
        if (tiles.length == 0) {
            return null;
        }
        return (InteractionFreezeExitTile) tiles[Emulator.getRandom().nextInt(tiles.length)];
    }

    public void addFreezeExitTile(InteractionFreezeExitTile freezeExitTile) {
        this.freezeExitTile.put(freezeExitTile.getId(), freezeExitTile);
        this.specialItemsById.put(freezeExitTile.getId(), freezeExitTile);
    }

    public Map<Integer, InteractionFreezeExitTile> getFreezeExitTiles() {
        return this.freezeExitTile;
    }

    public void removeFreezeExitTile(InteractionFreezeExitTile freezeExitTile) {
        this.freezeExitTile.remove(freezeExitTile.getId());
        this.specialItemsById.remove(freezeExitTile.getId());
    }

    public boolean hasFreezeExitTile() {
        return !this.freezeExitTile.isEmpty();
    }

    public void addUndefined(HabboItem item) {
        synchronized (this.undefined) {
            this.undefined.put(item.getId(), item);
        }
        this.specialItemsById.put(item.getId(), item);
    }

    public void removeUndefined(HabboItem item) {
        synchronized (this.undefined) {
            this.undefined.remove(item.getId());
        }
        this.specialItemsById.remove(item.getId());
    }

    public Set<HabboItem> getItemsOfType(Class<? extends HabboItem> type) {
        Set<HabboItem> items = new LinkedHashSet<>();

        // Check pet trees collection for InteractionPetTree type
        if (type == InteractionPetTree.class) {
            synchronized (this.petTrees) {
                items.addAll(this.petTrees.values());
            }
            return items;
        }

        // Check pet toys collection for InteractionPetToy type
        if (type == InteractionPetToy.class) {
            synchronized (this.petToys) {
                items.addAll(this.petToys.values());
            }
            return items;
        }

        synchronized (this.undefined) {
            for (HabboItem item : this.undefined.values()) {
                if (item.getClass() == type) items.add(item);
            }
        }

        return items;
    }

    public HabboItem getLowestItemsOfType(Class<? extends HabboItem> type) {
        HabboItem i = null;
        synchronized (this.undefined) {
            for (HabboItem item : this.undefined.values()) {
                if (i == null || item.getZ() < i.getZ()) {
                    if (item.getClass().isAssignableFrom(type)) {
                        i = item;
                    }
                }
            }
        }

        return i;
    }

    /**
     * Gets the set of cycle tasks.
     * @return The set of cycle tasks (thread-safe)
     */
    public Set<ICycleable> getCycleTasks() {
        return this.cycleTasks;
    }

    public void addCycleTask(ICycleable task) {
        this.cycleTasks.add(task);
    }

    public void removeCycleTask(ICycleable task) {
        this.cycleTasks.remove(task);
    }

    public HabboItem getSpecialItem(int itemId) {
        return this.specialItemsById.get(itemId);
    }

    public synchronized void dispose() {
        this.banzaiTeleporters.clear();
        this.nests.clear();
        this.petDrinks.clear();
        this.petFoods.clear();
        this.petToys.clear();
        this.petTrees.clear();
        synchronized (this.rollers) {
            this.rollers.clear();
        }

        this.triggers.clear();
        this.effects.clear();
        this.conditions.clear();
        this.extras.clear();

        this.gameScoreboards.clear();
        this.gameGates.clear();
        this.gameTimers.clear();

        this.freezeExitTile.clear();
        this.undefined.clear();
        this.cycleTasks.clear();
        this.specialItemsById.clear();
    }

    public Rectangle tentAt(RoomTile location) {
        for (HabboItem item : this.getItemsOfType(InteractionTent.class)) {
            Rectangle rectangle = RoomLayout.getRectangle(
                    item.getX(),
                    item.getY(),
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    item.getRotation());
            if (RoomLayout.tileInSquare(rectangle, location)) {
                return rectangle;
            }
        }

        return null;
    }
}
