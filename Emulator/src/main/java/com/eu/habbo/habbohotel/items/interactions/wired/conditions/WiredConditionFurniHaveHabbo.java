package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionFurniHaveHabbo extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.FURNI_HAVE_HABBO;
    protected THashSet<HabboItem> items;
    protected boolean all;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredConditionFurniHaveHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredConditionFurniHaveHabbo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.all = false;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();

        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty())
            return true;

        if (room.getLayout() == null)
            return false;

        Collection<Habbo> habbos = room.getHabbos();
        Collection<Bot> bots = room.getCurrentBots().valueCollection();
        Collection<Pet> pets = room.getCurrentPets().valueCollection();

        if (this.all) {
            return targets.stream().filter(item -> item != null).allMatch(item -> this.hasAvatarOnItem(item, room, habbos, bots, pets));
        }

        return targets.stream().filter(item -> item != null).anyMatch(item -> this.hasAvatarOnItem(item, room, habbos, bots, pets));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource,
                this.all
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.all = data.all;

            for(int id : WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 2) {
                for (int id : WiredFurniConditionInputGuard.parseLegacyItemIds(data[1], WiredManager.MAXIMUM_FURNI_SELECTION)) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.all = false;
        }
        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.all ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;

        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        int[] params = settings.getIntParams();
        this.all = (params.length > 0) && (params[0] == 1);
        this.furniSource = (params.length > 1) ? WiredFurniConditionInputGuard.normalizeFurniSource(params[1]) : ((params.length > 0 && params[0] > 1) ? WiredFurniConditionInputGuard.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER);

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) return false;

            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

                if (item != null)
                    this.items.add(item);
            }
        }

        return true;
    }

    int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    protected boolean hasAvatarOnItem(HabboItem item, Room room, Collection<Habbo> habbos, Collection<Bot> bots, Collection<Pet> pets) {
        RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
        if (baseTile == null) return false;

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(baseTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
        return occupiedTiles != null && (
                habbos.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation())) ||
                bots.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation())) ||
                pets.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation()))
        );
    }

    private void refresh() {
        THashSet<HabboItem> items = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
    }

    static class JsonData {
        List<Integer> itemIds;
        int furniSource;
        boolean all;

        public JsonData(List<Integer> itemIds, int furniSource, boolean all) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.all = all;
        }
    }
}
