package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSelector;
import polaris.messages.ServerMessage;

public class WiredEffectUsersOnFurni extends InteractionWiredEffect implements WiredOfficialSelector {
    public static final WiredEffectType type = WiredEffectType.USERS_ON_FURNI_SELECTOR;

    private final Set<UserItem> items = new LinkedHashSet<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersOnFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            ctx.targets().setUsers(Collections.emptySet());
            return;
        }

        this.refresh(room);

        // No source furni selects nothing; earlier selectors' picks stay, as with any other selector.
        List<UserItem> sourceItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (UserItem sourceItem : sourceItems) {
            result.addAll(this.resolveUnitsOnItem(room, sourceItem));
        }

        result = this.applySelectorModifiers(
                result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? this.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) {
                return false;
            }

            for (int itemId : settings.getFurniIds()) {
                UserItem item = room.getUserItem(itemId);

                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public boolean selectorFilter() {
        return this.filterExisting;
    }

    @Override
    public boolean selectorInvert() {
        return this.invert;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
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
        this.refresh(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.furniSource,
                        this.filterExisting,
                        this.invert,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.getDelay()));
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

        this.furniSource = this.normalizeFurniSource(data.furniSource);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);

        if (room == null || data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            UserItem item = room.getUserItem(id);

            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (UserItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        // Official layout: no int params, the furni in the official source slot.
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private Set<RoomUnit> resolveUnitsOnItem(Room room, UserItem sourceItem) {
        Set<RoomUnit> result = new LinkedHashSet<>();

        if (sourceItem == null) {
            return result;
        }

        RoomTile baseTile = room.getLayout().getTile(sourceItem.getX(), sourceItem.getY());
        if (baseTile == null) {
            return result;
        }

        Set<RoomTile> occupiedTiles = room.getLayout()
                .getTilesAt(
                        baseTile,
                        sourceItem.getBaseItem().getWidth(),
                        sourceItem.getBaseItem().getLength(),
                        sourceItem.getRotation());
        if (occupiedTiles == null) {
            return result;
        }

        for (RoomTile tile : occupiedTiles) {
            if (tile == null) {
                continue;
            }

            result.addAll(room.getUnitManager().getRoomUnitsAt(tile));
        }

        return result;
    }

    private void refresh(Room room) {
        Set<UserItem> invalidItems = new LinkedHashSet<>();

        if (room == null) {
            invalidItems.addAll(this.items);
        } else {
            for (UserItem item : this.items) {
                if (room.getUserItem(item.getId()) == null) {
                    invalidItems.add(item);
                }
            }
        }

        this.items.removeAll(invalidItems);
    }

    private int normalizeFurniSource(int value) {
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

    static class JsonData {
        int furniSource;
        boolean filterExisting;
        boolean invert;
        List<Integer> itemIds;
        int delay;

        JsonData(int furniSource, boolean filterExisting, boolean invert, List<Integer> itemIds, int delay) {
            this.furniSource = furniSource;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.itemIds = itemIds;
            this.delay = delay;
        }
    }
}
