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

public class WiredEffectUsersByType extends InteractionWiredEffect implements WiredOfficialSelector {
    private static final int ENTITY_USER = 1;
    private static final int ENTITY_PET = 2;
    private static final int ENTITY_BOT = 4;

    public static final WiredEffectType type = WiredEffectType.USERS_BY_TYPE_SELECTOR;

    private int entityType = ENTITY_USER;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            if (this.matchesType(roomUnit)) {
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
        this.entityType = (params.length > 0) ? this.normalizeEntityType(params[0]) : ENTITY_USER;
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
                .toJson(new JsonData(this.entityType, this.filterExisting, this.invert, this.getDelay()));
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

        this.entityType = this.normalizeEntityType(data.entityType);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.entityType = ENTITY_USER;
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
        message.appendInt(this.entityType);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    /** The official type mask 1-7; anything else is Habbo. */
    int normalizeEntityType(int value) {
        return value >= ENTITY_USER && value <= (ENTITY_USER | ENTITY_PET | ENTITY_BOT) ? value : ENTITY_USER;
    }

    private boolean matchesType(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        RoomUnitType roomUnitType = roomUnit.getRoomUnitType();
        // The official type mask: any of Habbo (1), pet (2) and bot (4).
        return roomUnitType != null && (roomUnitType.getTypeId() & this.entityType) != 0;
    }

    static class JsonData {
        int entityType;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int entityType, boolean filterExisting, boolean invert, int delay) {
            this.entityType = entityType;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
