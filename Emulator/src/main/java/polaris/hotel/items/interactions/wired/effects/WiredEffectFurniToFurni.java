package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSecondaryPicks;
import polaris.hotel.wired.protocol.WiredOfficialSources;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * Official move furni to furni (34): no int params. Furni source slot 0 is the furni to move (100 =
 * the picks), slot 1 the target furni (100 = the second picks, {@code stuffIds2}). Internally the
 * target's picks stay source 101.
 */
public class WiredEffectFurniToFurni extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialSecondaryPicks {
    private static final int SOURCE_SECONDARY_SELECTED = 101;
    private static final String FURNI_SPLIT_REGEX = "[;,\\t]";
    private static final String FURNI_DELIMITER = ";";

    public static final WiredEffectType type = WiredEffectType.FURNI_TO_FURNI;

    private final List<UserItem> moveItems = new ArrayList<>();
    private final List<UserItem> targetItems = new ArrayList<>();
    private int moveSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int targetSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectFurniToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniToFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        List<UserItem> moveItems = this.resolveMoveItems(ctx);
        List<UserItem> targetItems = this.resolveTargetItems(ctx);

        if (moveItems.isEmpty() || targetItems.isEmpty()) {
            return;
        }

        int targetIndex = 0;
        for (UserItem moveItem : moveItems) {
            if (moveItem == null) {
                continue;
            }

            UserItem targetItem = targetItems.get(targetIndex % targetItems.size());
            targetIndex++;

            if (targetItem == null || moveItem.getId() == targetItem.getId()) {
                continue;
            }

            RoomTile targetTile = room.getLayout().getTile(targetItem.getX(), targetItem.getY());
            if (targetTile == null) {
                continue;
            }

            // Habbo's stacking rules decide; a refused move (onto a chair, say) stays refused.
            // Movement Physics' "move through furni" is what lets it onto such furni.
            WiredMoveCarryHelper.moveFurni(room, this, moveItem, targetTile, moveItem.getRotation(), null, false, ctx);
        }
    }

    @Deprecated
    @Override
    public boolean execute(polaris.hotel.rooms.RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        this.moveItems.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.targetItems.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.moveSource,
                        this.targetSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.moveItems.clear();
        this.targetItems.clear();

        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            this.setDelay(data.delay);
            this.moveSource = data.moveSource;
            this.targetSource = this.normalizeTargetSource(data.targetSource);

            this.loadItems(room, data.itemIds, this.moveItems);
            this.loadItems(room, data.targetItemIds, this.targetItems);

            if (this.moveSource == WiredSourceUtil.SOURCE_TRIGGER && !this.moveItems.isEmpty()) {
                this.moveSource = WiredSourceUtil.SOURCE_SELECTED;
            }

            if (this.targetSource == WiredSourceUtil.SOURCE_TRIGGER && !this.targetItems.isEmpty()) {
                this.targetSource = SOURCE_SECONDARY_SELECTED;
            }

            return;
        }

        if (wiredData != null && !wiredData.isEmpty()) {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }

            if (wiredDataOld.length >= 2 && !wiredDataOld[1].trim().isEmpty()) {
                this.loadItems(room, this.parseIds(wiredDataOld[1], room), this.moveItems);
            }
        }

        this.moveSource = this.moveItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        this.targetSource = this.targetItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED;
    }

    @Override
    public void onPickUp() {
        this.moveItems.clear();
        this.targetItems.clear();
        this.moveSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.targetSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.moveItems);
        this.validateItems(this.targetItems);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.moveItems.size());

        for (UserItem item : this.moveItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        this.moveSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.targetSource = this.normalizeTargetSource(settings.furniSource(1, WiredSourceUtil.SOURCE_TRIGGER));

        Room room = this.getRoom();
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        // Picking furni with the dropdown left on "triggering furni" means the picked furni, the
        // same promotion loadWiredData already makes.
        if (settings.getFurniIds().length > 0 && this.moveSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.moveSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<UserItem> newMoveItems = new ArrayList<>();
        if (this.moveSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int itemId : settings.getFurniIds()) {
                UserItem item = room.getUserItem(itemId);

                if (item == null) {
                    throw new WiredSaveException(String.format("Item %s not found", itemId));
                }

                newMoveItems.add(item);
            }
        }

        if (newMoveItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<UserItem> newTargetItems = new ArrayList<>();
        if (this.targetSource == SOURCE_SECONDARY_SELECTED) {
            // The official second picks; an older editor sent them as text.
            int[] picks2 = settings.getFurniIds2();
            if (picks2.length > 0) {
                newTargetItems.addAll(this.parseItems(
                        Arrays.stream(picks2).mapToObj(Integer::toString).collect(Collectors.joining(FURNI_DELIMITER)),
                        room));
            } else {
                newTargetItems.addAll(this.parseItems(settings.getStringParam(), room));
            }
        }

        if (newTargetItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY)) {
            throw new WiredSaveException("Delay too long");
        }

        this.moveItems.clear();
        this.moveItems.addAll(newMoveItems);

        this.targetItems.clear();
        this.targetItems.addAll(newTargetItems);

        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    private List<UserItem> resolveMoveItems(WiredContext ctx) {
        return this.resolveItems(ctx, this.moveSource, this.moveItems);
    }

    private List<UserItem> resolveTargetItems(WiredContext ctx) {
        int source =
                (this.targetSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.targetSource;
        return this.resolveItems(ctx, source, this.targetItems);
    }

    private List<UserItem> resolveItems(WiredContext ctx, int source, List<UserItem> items) {
        if (source == WiredSourceUtil.SOURCE_SELECTED) {
            this.validateItems(items);
        }

        // Wall furni save as picks but take no part in the move, as in Habbo (dba6fcd8).
        return WiredSourceUtil.resolveItems(ctx, source, items).stream()
                .filter(item -> item != null && ctx.room().getUserItem(item.getId()) != null)
                .filter(item -> item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.WALL)
                .collect(Collectors.toList());
    }

    private List<UserItem> parseItems(String data, Room room) throws WiredSaveException {
        List<UserItem> items = new ArrayList<>();
        if (data == null || data.trim().isEmpty() || room == null) {
            return items;
        }

        Set<Integer> seen = new HashSet<>();

        for (String part : data.split(FURNI_SPLIT_REGEX)) {
            if (part == null) {
                continue;
            }

            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int itemId;
            try {
                itemId = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }

            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }

            UserItem item = room.getUserItem(itemId);
            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            items.add(item);
        }

        return items;
    }

    private List<Integer> parseIds(String data, Room room) {
        try {
            return this.parseItems(data, room).stream().map(UserItem::getId).collect(Collectors.toList());
        } catch (WiredSaveException e) {
            return new ArrayList<>();
        }
    }

    private void loadItems(Room room, List<Integer> itemIds, List<UserItem> target) {
        if (room == null || itemIds == null || itemIds.isEmpty()) {
            return;
        }

        for (Integer itemId : itemIds) {
            UserItem item = room.getUserItem(itemId);

            if (item != null) {
                target.add(item);
            }
        }
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {
            this.moveSource,
            (this.targetSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.targetSource
        };
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public int[] officialFurniIds2() {
        return this.targetItems.stream().mapToInt(UserItem::getId).toArray();
    }

    private int normalizeTargetSource(int source) {
        return (source == WiredSourceUtil.SOURCE_SELECTED) ? SOURCE_SECONDARY_SELECTED : source;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        List<Integer> targetItemIds;
        int moveSource;
        int targetSource;

        public JsonData(
                int delay, List<Integer> itemIds, List<Integer> targetItemIds, int moveSource, int targetSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.targetItemIds = targetItemIds;
            this.moveSource = moveSource;
            this.targetSource = targetSource;
        }
    }
}
