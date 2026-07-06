package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes the resolved furni from the room when the stack fires — the inverse of {@link WiredEffectPlaceFurni}.
 * Targets are the builder-selected furni (resolved through {@link WiredSourceUtil#resolveItems}); two modes:
 * RETURN_TO_INVENTORY (default, non-destructive — picks the item up to its owner's inventory) or DELETE
 * (permanently destroys it). It never removes WIRED furni or itself (stack-safety) and is bounded per fire.
 *
 * <p>Carries two ints — {@code [mode, furniSource]} — plus the furni-id selection block, so it needs the matching
 * Nitro {@code WiredActionLayoutCode.REMOVE_FURNI} (107) dialog with the room-furni picker.</p>
 */
public class WiredEffectRemoveFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.REMOVE_FURNI;

    public static final int MODE_RETURN = 0;
    public static final int MODE_DELETE = 1;

    private static final int MAX_REMOVE_PER_FIRE = 100;

    private final Set<Integer> selectedItemIds = new HashSet<>();
    private int mode = MODE_RETURN;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;

    public WiredEffectRemoveFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoveFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        this.refresh();

        List<HabboItem> selected = new ArrayList<>();
        for (int id : this.selectedItemIds) {
            HabboItem it = room.getHabboItem(id);
            if (it != null) selected.add(it);
        }

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, selected);
        if (targets.isEmpty()) return;

        int removed = 0;
        for (HabboItem target : targets) {
            if (target == null) continue;
            if (target.getId() == this.getId()) continue;       // never remove self
            if (target instanceof InteractionWired) continue;   // never remove other wired furni (stack-safety)
            if (target.getRoomId() != room.getId()) continue;   // only items currently in this room
            if (removed >= MAX_REMOVE_PER_FIRE) break;

            if (this.mode == MODE_DELETE) {
                room.pickUpItem(target, null);
                Emulator.getGameEnvironment().getItemManager().deleteItem(target);
            } else {
                int ownerId = target.getUserId();
                Habbo ownerHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(ownerId);
                boolean tracked = BuildersClubRoomSupport.isTrackedItem(target.getId());

                room.pickUpItem(target, ownerHabbo);

                if (ownerHabbo != null && ownerHabbo.getClient() != null && !tracked) {
                    ownerHabbo.getInventory().getItemsComponent().addItem(target);
                    ownerHabbo.getClient().sendResponse(new AddHabboItemComposer(target));
                    ownerHabbo.getClient().sendResponse(new InventoryRefreshComposer());
                }
            }

            removed++;
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedItemIds.size());

        for (int id : this.selectedItemIds) {
            message.appendInt(id);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.mode);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.mode = (params.length > 0 && params[0] == MODE_DELETE) ? MODE_DELETE : MODE_RETURN;
        this.furniSource = (params.length > 1) ? params[1] : WiredSourceUtil.SOURCE_SELECTED;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Trying to save wired in unloaded room");
        }

        int itemsCount = settings.getFurniIds().length;
        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.selectedItemIds.clear();
        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);
            if (it == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }
            this.selectedItemIds.add(itemId);
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }
        this.setDelay(delay);

        return true;
    }

    private void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null && room.isLoaded()) {
            this.selectedItemIds.removeIf(id -> room.getHabboItem(id) == null);
        }
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(new ArrayList<>(this.selectedItemIds), this.mode, this.furniSource, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.selectedItemIds.clear();
            if (data.items != null) this.selectedItemIds.addAll(data.items);
            this.mode = (data.mode == MODE_DELETE) ? MODE_DELETE : MODE_RETURN;
            this.furniSource = data.furniSource;
            this.setDelay(data.delay);
        } else {
            this.selectedItemIds.clear();
            this.mode = MODE_RETURN;
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.selectedItemIds.clear();
        this.mode = MODE_RETURN;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        List<Integer> items;
        int mode;
        int furniSource;
        int delay;

        public JsonData(List<Integer> items, int mode, int furniSource, int delay) {
            this.items = items;
            this.mode = mode;
            this.furniSource = furniSource;
            this.delay = delay;
        }
    }
}
