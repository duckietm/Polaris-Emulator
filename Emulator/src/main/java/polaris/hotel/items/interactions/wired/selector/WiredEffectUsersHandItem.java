package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.protocol.WiredOfficialSelector;
import polaris.messages.ServerMessage;

public class WiredEffectUsersHandItem extends InteractionWiredEffect implements WiredOfficialSelector {
    public static final WiredEffectType type = WiredEffectType.USERS_HANDITEM_SELECTOR;

    private int handItemId = 0;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersHandItem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            // AIR: only avatars, holding exactly this hand item; 0 ("None" in the editor) is an empty hand.
            if (roomUnit != null
                    && roomUnit.getRoomUnitType() == RoomUnitType.USER
                    && roomUnit.getHandItem() == this.handItemId) {
                result.add(roomUnit);
            }
        }

        result = this.applySelectorModifiers(
                result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        // Stored as sent, as AIR does.
        this.handItemId = (params.length > 0) ? params[0] : 0;
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();
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
                .toJson(new JsonData(this.handItemId, this.filterExisting, this.invert, this.getDelay()));
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

        this.handItemId = data.handItemId;
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.handItemId = 0;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.handItemId);
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
        int handItemId;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int handItemId, boolean filterExisting, boolean invert, int delay) {
            this.handItemId = handItemId;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
