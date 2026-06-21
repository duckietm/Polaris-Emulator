package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectFurniPicks extends InteractionWiredEffect {
    private static final int MAX_PICKED_FURNI = 20;

    public static final WiredEffectType type = WiredEffectType.FURNI_PICKS_SELECTOR;

    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> pickedFurniIds = new ArrayList<>();

    public WiredEffectFurniPicks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniPicks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        Set<HabboItem> result = this.pickedFurniIds.stream()
                .map(room::getHabboItem)
                .filter(item -> item != null && (includeWiredItems || !(item instanceof InteractionWired)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        result = this.applySelectorModifiers(result, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);

        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.filterExisting = params.length > 0 && params[0] == 1;
        this.invert = params.length > 1 && params[1] == 1;

        this.pickedFurniIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (this.pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                this.pickedFurniIds.add(id);
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
        return WiredManager.getGson().toJson(new JsonData(
                this.filterExisting,
                this.invert,
                this.pickedFurniIds,
                this.getDelay()
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.pickedFurniIds = (data.pickedFurniIds != null) ? data.pickedFurniIds : new ArrayList<>();
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.filterExisting = false;
        this.invert = false;
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(MAX_PICKED_FURNI);

        if (!this.pickedFurniIds.isEmpty()) {
            message.appendInt(this.pickedFurniIds.size());
            this.pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
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

    static class JsonData {
        boolean filterExisting;
        boolean invert;
        List<Integer> pickedFurniIds;
        int delay;

        JsonData(boolean filterExisting, boolean invert, List<Integer> pickedFurniIds, int delay) {
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay = delay;
        }
    }
}
