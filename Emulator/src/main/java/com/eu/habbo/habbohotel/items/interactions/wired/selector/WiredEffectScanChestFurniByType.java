package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chest Furni-of-Type scanner (furni classname {@code wf_xtra_scan_chest_furni_by_type}). A read-only
 * selector that picks the room furni whose base-type matches a type stored in a selected
 * {@link InteractionWiredChest}. (The config-based chest holds virtual contents, not real instances —
 * so "scan chest furni by type" resolves to room furni of the chest's stored types. When an authentic
 * drag-to-fill chest exists, this can be upgraded to scan real deposited furni.)
 */
public class WiredEffectScanChestFurniByType extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SCAN_CHEST_FURNI_BY_TYPE;
    private static final int MAX_PICKED_FURNI = 20;

    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> chestIds = new ArrayList<>();

    public WiredEffectScanChestFurniByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectScanChestFurniByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        // Collect the distinct furni base-types stored across the selected chest(s).
        Set<Integer> chestTypes = new HashSet<>();
        for (Integer id : this.chestIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest chest) {
                chestTypes.addAll(chest.getContents().distinctTypes(ChestStorage.KIND_FURNI));
            }
        }

        Set<HabboItem> matched = new LinkedHashSet<>();
        if (!chestTypes.isEmpty()) {
            room.getFloorItems().forEach(item -> {
                if (item == null) return;
                if (!includeWiredItems && item instanceof InteractionWired) return;
                if (chestTypes.contains(item.getBaseItem().getId())) {
                    matched.add(item);
                }
            });
        }

        Set<HabboItem> result = this.applySelectorModifiers(matched, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);
        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.filterExisting = params.length > 0 && params[0] == 1;
        this.invert = params.length > 1 && params[1] == 1;

        this.chestIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (this.chestIds.size() >= MAX_PICKED_FURNI) break;
                this.chestIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.filterExisting, this.invert, this.chestIds, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.chestIds = (data.chestIds != null) ? data.chestIds : new ArrayList<>();
        this.setDelay(data.delay);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(MAX_PICKED_FURNI);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        this.filterExisting = false;
        this.invert = false;
        this.chestIds = new ArrayList<>();
        this.setDelay(0);
    }

    static class JsonData {
        boolean filterExisting;
        boolean invert;
        List<Integer> chestIds;
        int delay;

        JsonData(boolean filterExisting, boolean invert, List<Integer> chestIds, int delay) {
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.chestIds = chestIds;
            this.delay = delay;
        }
    }
}
