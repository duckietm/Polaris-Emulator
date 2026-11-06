package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.protocol.WiredOfficialSelector;
import polaris.messages.ServerMessage;

/**
 * The custom selector {@code wf_xtra_scan_chest_furni_by_type} used to load as: it picked the room furni whose
 * base-type matches a type stored in a selected {@link InteractionWiredChest}.
 *
 * @deprecated no longer registered; the classname loads the official chest item type scanner add-on,
 *     {@link polaris.hotel.items.interactions.wired.extra.WiredExtraChestItemTypeScanner}. Kept so
 *     plugins compiled against it still link.
 */
@Deprecated
public class WiredEffectScanChestFurniByType extends InteractionWiredEffect implements WiredOfficialSelector {
    public static final WiredEffectType type = WiredEffectType.SCAN_CHEST_FURNI_BY_TYPE;
    private static final int MAX_PICKED_FURNI = 20;

    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> chestIds = new ArrayList<>();

    public WiredEffectScanChestFurniByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectScanChestFurniByType(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            UserItem item = room.getUserItem(id);
            // A chest that does not answer wired keeps its contents to itself, as it does for the
            // chest effects and Init Transaction; a read-only scan still discloses what is inside.
            if (item instanceof InteractionWiredChest chest && chest.answersWired()) {
                chestTypes.addAll(chest.getContents().distinctTypes(ChestStorage.KIND_FURNI));
            }
        }

        Set<UserItem> matched = new LinkedHashSet<>();
        if (!chestTypes.isEmpty()) {
            room.getFloorItems().forEach(item -> {
                if (item == null) return;
                if (!includeWiredItems && item instanceof InteractionWired) return;
                if (chestTypes.contains(item.getBaseItem().getId())) {
                    matched.add(item);
                }
            });
        }

        Set<UserItem> result = this.applySelectorModifiers(
                matched, this.getSelectableItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);
        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

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
    public boolean selectorFilter() {
        return this.filterExisting;
    }

    @Override
    public boolean selectorInvert() {
        return this.invert;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[0];
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
        return WiredManager.getGson()
                .toJson(new JsonData(this.filterExisting, this.invert, this.chestIds, this.getDelay()));
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
