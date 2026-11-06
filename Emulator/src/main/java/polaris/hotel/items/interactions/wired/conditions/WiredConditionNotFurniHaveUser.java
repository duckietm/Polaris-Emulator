package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

public class WiredConditionNotFurniHaveUser extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.NOT_FURNI_HAVE_USER;

    /** Official param 0: 1 (the first option) any picked furni is free, 0 every picked furni is free. */
    static final int OFFICIAL_ANY_FREE = 1;

    static final int OFFICIAL_ALL_FREE = 0;

    protected Set<UserItem> items;
    /** Every picked furni must be free of avatars (official 0); otherwise one free furni is enough (official 1). */
    protected boolean all;

    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredConditionNotFurniHaveUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredConditionNotFurniHaveUser(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.all = false;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();

        this.refresh();

        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty()) return true;

        if (room.getLayout() == null) return false;

        Collection<User> users = room.getUsers();
        Collection<Bot> bots = room.getCurrentBots().values();
        Collection<Pet> pets = room.getCurrentPets().values();

        if (this.all) {
            return targets.stream()
                    .filter(item -> item != null)
                    .allMatch(item -> !this.hasAvatarOnItem(item, room, users, bots, pets));
        }

        return targets.stream()
                .filter(item -> item != null)
                .anyMatch(item -> !this.hasAvatarOnItem(item, room, users, bots, pets));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.furniSource,
                        this.all));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            WiredConditionFurniHaveUser.JsonData data =
                    WiredManager.getGson().fromJson(wiredData, WiredConditionFurniHaveUser.JsonData.class);
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.all = data.all;

            for (int id :
                    WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                UserItem item = room.getUserItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 2) {
                for (int id : WiredFurniConditionInputGuard.parseLegacyItemIds(
                        data[1], WiredManager.MAXIMUM_FURNI_SELECTION)) {
                    UserItem item = room.getUserItem(id);

                    if (item != null) this.items.add(item);
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.all = false;
        }
        this.furniSource =
                WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
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

        for (UserItem item : this.items) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.all ? OFFICIAL_ALL_FREE : OFFICIAL_ANY_FREE);
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
        // Official layout (AIR FurnisHaveNoAvatars): param 0 is 1 for the first option, "one of the picked
        // furni has no avatar", and 0 for "none of them has one"; the furni in the official source slot.
        // The stored flag keeps its meaning (every furni free), so saved boxes need no conversion.
        this.all = (params.length > 0) && (params[0] == OFFICIAL_ALL_FREE);
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? WiredFurniConditionInputGuard.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) return false;

            for (int i = 0; i < count; i++) {
                UserItem item = room.getUserItem(settings.getFurniIds()[i]);

                if (item != null) this.items.add(item);
            }
        }

        return true;
    }

    protected boolean hasAvatarOnItem(
            UserItem item, Room room, Collection<User> users, Collection<Bot> bots, Collection<Pet> pets) {
        RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
        if (baseTile == null) return false;

        Set<RoomTile> occupiedTiles = room.getLayout()
                .getTilesAt(
                        baseTile,
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation());
        return occupiedTiles != null
                && (users.stream()
                                .anyMatch(character -> character.getRoomUnit() != null
                                        && occupiedTiles.contains(
                                                character.getRoomUnit().getCurrentLocation()))
                        || bots.stream()
                                .anyMatch(character -> character.getRoomUnit() != null
                                        && occupiedTiles.contains(
                                                character.getRoomUnit().getCurrentLocation()))
                        || pets.stream()
                                .anyMatch(character -> character.getRoomUnit() != null
                                        && occupiedTiles.contains(
                                                character.getRoomUnit().getCurrentLocation())));
    }

    private void refresh() {
        Set<UserItem> items = new HashSet<>();

        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (UserItem item : this.items) {
                if (room.getUserItem(item.getId()) == null) items.add(item);
            }
        }

        for (UserItem item : items) {
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
