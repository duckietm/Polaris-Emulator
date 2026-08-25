package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredExtraUnseen extends InteractionWiredExtra {
    public static final int CODE = 62;

    /**
     * Maximum number of effect IDs to track to prevent memory leaks.
     * When limit is reached, oldest entries are removed automatically.
     */
    private static final int MAX_SEEN_LIST_SIZE = 1000;
    
    /**
     * Thread-safe set of seen effect IDs. Uses LinkedHashSet for insertion order
     * to support LRU-style eviction when max size is reached.
     */
    private final Set<Integer> seenList = new LinkedHashSet<>();

    public WiredExtraUnseen(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraUnseen(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData());
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
    }

    @Override
    public void onPickUp() {
        synchronized (this.seenList) {
            this.seenList.clear();
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        synchronized (this.seenList) {
            this.seenList.clear();
        }
    }

    public InteractionWiredEffect getUnseenEffect(List<InteractionWiredEffect> effects) {
        synchronized (this.seenList) {
            List<InteractionWiredEffect> unseenEffects = new ArrayList<>();
            for (InteractionWiredEffect effect : effects) {
                if (!this.seenList.contains(effect.getId())) {
                    unseenEffects.add(effect);
                }
            }

            InteractionWiredEffect effect = null;
            if (!unseenEffects.isEmpty()) {
                effect = unseenEffects.get(0);
            } else {
                this.seenList.clear();

                if (!effects.isEmpty()) {
                    effect = effects.get(0);
                }
            }

            if (effect != null) {
                // Enforce max size limit to prevent memory leaks
                if (this.seenList.size() >= MAX_SEEN_LIST_SIZE) {
                    // Remove oldest entry (first in insertion order)
                    Integer oldest = this.seenList.iterator().next();
                    this.seenList.remove(oldest);
                }
                this.seenList.add(effect.getId());
            }
            return effect;
        }
    }

    public List<IWiredEffect> selectWiredEffects(List<IWiredEffect> effects) {
        synchronized (this.seenList) {
            List<IWiredEffect> unseenEffects = new ArrayList<>();

            for (IWiredEffect effect : effects) {
                if ((effect instanceof InteractionWiredEffect)
                        && !this.seenList.contains(((InteractionWiredEffect) effect).getId())) {
                    unseenEffects.add(effect);
                }
            }

            IWiredEffect effect = null;
            if (!unseenEffects.isEmpty()) {
                effect = unseenEffects.get(0);
            } else {
                this.seenList.clear();

                if (!effects.isEmpty()) {
                    effect = effects.get(0);
                }
            }

            if (effect instanceof InteractionWiredEffect) {
                if (this.seenList.size() >= MAX_SEEN_LIST_SIZE) {
                    Integer oldest = this.seenList.iterator().next();
                    this.seenList.remove(oldest);
                }

                this.seenList.add(((InteractionWiredEffect) effect).getId());
            }

            if (effect == null) {
                return new ArrayList<>();
            }

            List<IWiredEffect> selectedEffects = new ArrayList<>();
            selectedEffects.add(effect);
            return selectedEffects;
        }
    }
    
    /**
     * Gets the current size of the seen list.
     * @return the number of tracked effect IDs
     */
    public int getSeenListSize() {
        synchronized (this.seenList) {
            return this.seenList.size();
        }
    }
    
    /**
     * Clears the seen list.
     */
    public void clearSeenList() {
        synchronized (this.seenList) {
            this.seenList.clear();
        }
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    static class JsonData {
    }
}
