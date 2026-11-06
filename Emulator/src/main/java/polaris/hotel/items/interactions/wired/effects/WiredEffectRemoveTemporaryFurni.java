package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSources;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * The official "remove furni" action (AIR code 56, furni {@code wf_act_remove_furni}): takes the
 * temporary furni that "place furni" put in the room out again. Furni somebody owns is left where it is,
 * however it was selected. No int params; one furni source (trigger, picks, selector or signal).
 */
public class WiredEffectRemoveTemporaryFurni extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.REMOVE_FURNI;

    private final Set<Integer> selectedItemIds = new LinkedHashSet<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectRemoveTemporaryFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoveTemporaryFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) return;
        Room room = ctx.room();
        if (room == null) return;

        List<UserItem> picked = new ArrayList<>();
        for (int id : this.selectedItemIds) {
            UserItem item = room.getUserItem(id);
            if (item != null) picked.add(item);
        }

        // One ObjectRemoveMultiple for the whole removal, as in Habbo; an owner pick-up stays one ObjectRemove.
        List<UserItem> copies = new ArrayList<>();
        for (UserItem item : WiredSourceUtil.resolveItems(ctx, this.furniSource, picked)) {
            if (item != null && item.isTemporary() && room.getUserItem(item.getId()) == item) {
                copies.add(item);
            }
        }
        if (!copies.isEmpty()) {
            room.getItemManager().removeTemporaryItems(copies);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] sources = settings.getFurniSourceTypes();
        this.furniSource = normalizeSource(sources.length > 0 ? sources[0] : WiredSourceUtil.SOURCE_TRIGGER);

        int[] picks = settings.getFurniIds();
        ConfigurationManager config = WiredPlatform.configuration();
        int cap = config == null
                ? WiredManager.MAXIMUM_FURNI_SELECTION
                : config.getInt("hotel.wired.furni.selection.count");
        if (picks.length > cap) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.selectedItemIds.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int id : picks) this.selectedItemIds.add(id);
        }

        this.setDelay(settings.getDelay());
        return true;
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

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<Integer> live = new ArrayList<>();
        for (int id : this.selectedItemIds) {
            if (room == null || room.getUserItem(id) != null) live.add(id);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(live.size());
        for (int id : live) message.appendInt(id);
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
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(new ArrayList<>(this.selectedItemIds), this.furniSource, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        // A box saved by our earlier "pick up to inventory" element under this name reads the same fields
        // (its mode is dropped): it now removes only temporary furni.
        JsonData data = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.furniSource = normalizeSource(data.furniSource);
        if (data.items != null && this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (Integer id : data.items) {
                if (id != null && id > 0) this.selectedItemIds.add(id);
            }
        }
        this.setDelay(WiredInputGuard.normalizeDelay(data.delay));
    }

    @Override
    public void onPickUp() {
        this.selectedItemIds.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    private static int normalizeSource(int source) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL ->
                source;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    static class JsonData {
        List<Integer> items;
        int furniSource;
        int delay;

        JsonData(List<Integer> items, int furniSource, int delay) {
            this.items = items;
            this.furniSource = furniSource;
            this.delay = delay;
        }
    }
}
