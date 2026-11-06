package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import polaris.WiredPlatform;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.api.IWiredEffect;
import polaris.hotel.wired.core.WiredContext;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.wired.WiredEffectDataComposer;

/**
 * Base class for all wired effects in the game.
 * <p>
 * Wired effects are triggered by {@link InteractionWiredTrigger} when conditions
 * (defined by {@link InteractionWiredCondition}) are met. Effects can perform
 * various actions like moving furniture, teleporting users, toggling states, etc.
 * </p>
 * <p>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #execute(RoomUnit, Room, Object[])} - The actual effect logic</li>
 *   <li>{@link #getType()} - Returns the effect type enum</li>
 *   <li>{@link #saveData(WiredSettings, GameClient)} - Saves configuration from client</li>
 *   <li>{@link #getWiredData()} - Serializes data for database storage</li>
 *   <li>{@link #loadWiredData(java.sql.ResultSet, Room)} - Loads data from database</li>
 *   <li>{@link #serializeWiredData(polaris.messages.ServerMessage, Room)} - Sends config to client</li>
 * </ul>
 * </p>
 *
 * @see InteractionWiredTrigger
 * @see InteractionWiredCondition
 * @see polaris.hotel.wired.core.WiredManager
 */
public abstract class InteractionWiredEffect extends InteractionWired implements IWiredEffect {

    // Common cooldown constants (in milliseconds)
    /** No cooldown - effect can trigger as fast as possible */
    public static final long COOLDOWN_NONE = 0L;
    /** Default cooldown for most effects */
    public static final long COOLDOWN_DEFAULT = 50L;
    /** Cooldown for movement effects (move to, move towards, move away) */
    public static final long COOLDOWN_MOVEMENT = 495L;
    /** Cooldown for trigger stacks effect to prevent rapid re-triggering */
    public static final long COOLDOWN_TRIGGER_STACKS = 250L;
    /** Cooldown for teleport effect */
    public static final long COOLDOWN_TELEPORT = 500L;

    private int delay;

    public InteractionWiredEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getUser())) {
                client.sendResponse(new WiredEffectDataComposer(this, room));
                this.activateBox(room);
            }
        }
    }

    public abstract boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException;

    public int getDelay() {
        return this.delay;
    }

    protected void setDelay(int value) {
        this.delay = value;
    }

    public abstract WiredEffectType getType();

    // ========== IWiredEffect Implementation ==========

    /**
     * Executes this effect with the given context.
     * Subclasses must implement this to define their effect logic.
     *
     * @param ctx the wired context containing event data
     */
    @Override
    public abstract void execute(WiredContext ctx);

    /**
     * Returns whether this effect requires an actor (user) to execute.
     */
    @Override
    public boolean requiresActor() {
        return requiresTriggeringUser();
    }

    /**
     * Indicates whether this effect requires a triggering user (RoomUnit) to execute.
     * Effects that require a user will not execute if no user triggered them.
     *
     * @return true if this effect requires a triggering user, false otherwise
     */
    public boolean requiresTriggeringUser() {
        return false;
    }

    /**
     * Gets the room this wired effect is placed in.
     * Convenience method to avoid repeated lookups.
     *
     * @return the Room, or null if not found
     */
    protected Room getRoom() {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }

        return WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }

    /**
     * Validates and cleans a collection of items, removing those that are no longer valid.
     * An item is invalid if:
     * <ul>
     *   <li>It's null</li>
     *   <li>Its room ID doesn't match this effect's room ID</li>
     *   <li>It no longer exists in the room</li>
     * </ul>
     *
     * @param items the collection of items to validate
     * @param <T> the type extending UserItem
     * @return the number of items removed
     */
    protected <T extends UserItem> int validateItems(Collection<T> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        Room room = this.getRoom();
        if (room == null) {
            int size = items.size();
            items.clear();
            return size;
        }

        int roomId = this.getRoomId();
        int sizeBefore = items.size();
        items.removeIf(item -> item == null || item.getRoomId() != roomId || room.getUserItem(item.getId()) == null);
        return sizeBefore - items.size();
    }

    /**
     * Validates and cleans a collection of items with an additional custom predicate.
     * Items matching the predicate will be removed in addition to standard validation.
     *
     * @param items the collection of items to validate
     * @param additionalRemoveCondition additional condition that, if true, causes removal
     * @param <T> the type extending UserItem
     * @return the number of items removed
     */
    protected <T extends UserItem> int validateItems(Collection<T> items, Predicate<T> additionalRemoveCondition) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        Room room = this.getRoom();
        if (room == null) {
            int size = items.size();
            items.clear();
            return size;
        }

        int roomId = this.getRoomId();
        int sizeBefore = items.size();
        items.removeIf(item -> item == null
                || item.getRoomId() != roomId
                || room.getUserItem(item.getId()) == null
                || additionalRemoveCondition.test(item));
        return sizeBefore - items.size();
    }

    protected <T> LinkedHashSet<T> applySelectorModifiers(
            Iterable<T> matchedTargets,
            Iterable<T> availableTargets,
            Iterable<T> existingTargets,
            boolean filterExisting,
            boolean invert) {
        LinkedHashSet<T> matched = toLinkedHashSet(matchedTargets);
        LinkedHashSet<T> base = filterExisting ? toLinkedHashSet(existingTargets) : toLinkedHashSet(availableTargets);

        if (filterExisting) {
            if (invert) {
                base.removeAll(matched);
                return base;
            }

            matched.retainAll(base);
            return matched;
        }

        // An ordinary selector adds to what the stack's earlier selectors picked, as in Habbo;
        // it used to replace it, so only the last selector in a stack counted.
        LinkedHashSet<T> result = toLinkedHashSet(existingTargets);

        if (invert) {
            base.removeAll(matched);
            result.addAll(base);
        } else {
            result.addAll(matched);
        }

        return result;
    }

    protected LinkedHashSet<UserItem> getSelectableFloorItems(Room room) {
        return this.getSelectableFloorItems(room, null);
    }

    protected LinkedHashSet<UserItem> getSelectableFloorItems(Room room, WiredContext ctx) {
        LinkedHashSet<UserItem> result = new LinkedHashSet<>();
        if (room == null) {
            return result;
        }

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        room.getFloorItems().forEach(item -> {
            if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                result.add(item);
            }
        });

        return result;
    }

    /** Every furni a furni selector inverts over, as AIR does: floor and wall furni, wired boxes included. */
    protected LinkedHashSet<UserItem> getSelectableItems(Room room, WiredContext ctx) {
        LinkedHashSet<UserItem> result = this.getSelectableFloorItems(room, ctx);
        if (room != null) {
            room.getWallItems().forEach(item -> {
                if (item != null) {
                    result.add(item);
                }
            });
        }

        return result;
    }

    /**
     * Whether furni selectors keep wired boxes. AIR's always do (Furni In Area over a box stack selects every box),
     * so this is true; plugins may still override it.
     */
    protected boolean includeWiredTargets(WiredContext ctx) {
        return true;
    }

    protected <T> LinkedHashSet<T> toLinkedHashSet(Iterable<T> values) {
        LinkedHashSet<T> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }

        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }

        return result;
    }
}
