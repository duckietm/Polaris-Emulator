package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents the target users and items for wired effect execution.
 * <p>
 * This class provides a mutable collection of targets that can be modified
 * by selectors (future feature) before effects are executed. By default,
 * targets include the triggering actor and trigger item.
 * </p>
 *
 * <h3>Future Selector Support:</h3>
 * <pre>{@code
 * // Selectors will be able to filter/modify targets:
 * targets.setUsers(targets.users().stream()
 *     .filter(u -> u.hasEffect(123))
 *     .collect(Collectors.toList()));
 * }</pre>
 *
 * @see WiredContext
 */
public final class WiredTargets {

    private final Set<RoomUnit> users = new LinkedHashSet<>();
    private final Set<HabboItem> items = new LinkedHashSet<>();
    private boolean itemsModifiedBySelector = false;
    private boolean usersModifiedBySelector = false;

    /**
     * Get all targeted users (read-only view).
     * @return unmodifiable set of room units
     */
    public Set<RoomUnit> users() {
        return Collections.unmodifiableSet(users);
    }

    /**
     * Get all targeted items (read-only view).
     * @return unmodifiable set of habbo items
     */
    public Set<HabboItem> items() {
        return Collections.unmodifiableSet(items);
    }

    /**
     * Check if there are any user targets.
     * @return true if at least one user is targeted
     */
    public boolean hasUsers() {
        return !users.isEmpty();
    }

    /**
     * Check if there are any item targets.
     * @return true if at least one item is targeted
     */
    public boolean hasItems() {
        return !items.isEmpty();
    }

    /**
     * Check if item targets were explicitly set by a selector.
     * Effects should use this to determine whether to use selector targets
     * instead of their own manually configured items.
     * @return true if a selector has called setItems()
     */
    public boolean isItemsModifiedBySelector() {
        return itemsModifiedBySelector;
    }

    /**
     * Check if user targets were explicitly set by a selector.
     * @return true if a selector has called setUsers()
     */
    public boolean isUsersModifiedBySelector() {
        return usersModifiedBySelector;
    }

    /**
     * Check if there are any targets at all.
     * @return true if there are users or items
     */
    public boolean hasTargets() {
        return hasUsers() || hasItems();
    }

    /**
     * Get the first user target, if any.
     * Useful when you expect exactly one user target.
     * @return the first user, or null if no users
     */
    public RoomUnit firstUser() {
        return users.isEmpty() ? null : users.iterator().next();
    }

    /**
     * Get the first item target, if any.
     * Useful when you expect exactly one item target.
     * @return the first item, or null if no items
     */
    public HabboItem firstItem() {
        return items.isEmpty() ? null : items.iterator().next();
    }

    // Mutators (used by engine/selector layer)

    /**
     * Clear all targets.
     */
    public void clear() {
        users.clear();
        items.clear();
    }

    /**
     * Clear all user targets.
     */
    public void clearUsers() {
        users.clear();
    }

    /**
     * Clear all item targets.
     */
    public void clearItems() {
        items.clear();
    }

    /**
     * Replace all user targets with the given users.
     * @param newUsers the new user targets
     */
    public void setUsers(Iterable<RoomUnit> newUsers) {
        users.clear();
        usersModifiedBySelector = true;
        if (newUsers != null) {
            for (RoomUnit u : newUsers) {
                if (u != null) users.add(u);
            }
        }
    }

    /**
     * Replace all item targets with the given items.
     * @param newItems the new item targets
     */
    public void setItems(Iterable<HabboItem> newItems) {
        items.clear();
        itemsModifiedBySelector = true;
        if (newItems != null) {
            for (HabboItem i : newItems) {
                if (i != null) items.add(i);
            }
        }
    }

    /** Restore a previously captured delayed target set without changing selector provenance. */
    void restoreSnapshot(
            Iterable<RoomUnit> newUsers,
            Iterable<HabboItem> newItems,
            boolean usersModifiedBySelector,
            boolean itemsModifiedBySelector) {
        this.users.clear();
        this.items.clear();
        if (newUsers != null) {
            for (RoomUnit user : newUsers) {
                if (user != null) {
                    this.users.add(user);
                }
            }
        }
        if (newItems != null) {
            for (HabboItem item : newItems) {
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
        this.usersModifiedBySelector = usersModifiedBySelector;
        this.itemsModifiedBySelector = itemsModifiedBySelector;
    }

    /**
     * Add a user to the targets.
     * @param user the user to add (null is ignored)
     */
    public void addUser(RoomUnit user) {
        if (user != null) users.add(user);
    }

    /**
     * Add an item to the targets.
     * @param item the item to add (null is ignored)
     */
    public void addItem(HabboItem item) {
        if (item != null) items.add(item);
    }

    /**
     * Remove a user from the targets.
     * @param user the user to remove
     * @return true if the user was removed
     */
    public boolean removeUser(RoomUnit user) {
        return users.remove(user);
    }

    /**
     * Remove an item from the targets.
     * @param item the item to remove
     * @return true if the item was removed
     */
    public boolean removeItem(HabboItem item) {
        return items.remove(item);
    }

    /**
     * Get the count of user targets.
     * @return number of users
     */
    public int userCount() {
        return users.size();
    }

    /**
     * Get the count of item targets.
     * @return number of items
     */
    public int itemCount() {
        return items.size();
    }

    @Override
    public String toString() {
        return "WiredTargets{" + "users=" + users.size() + ", items=" + items.size() + '}';
    }
}
