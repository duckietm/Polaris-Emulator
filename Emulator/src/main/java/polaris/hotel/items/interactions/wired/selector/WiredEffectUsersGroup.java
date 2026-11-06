package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.protocol.WiredOfficialSelector;
import polaris.messages.ServerMessage;

public class WiredEffectUsersGroup extends InteractionWiredEffect implements WiredOfficialSelector {
    private static final int GROUP_CURRENT_ROOM = 0;
    private static final int GROUP_SELECTED = 1;

    public static final WiredEffectType type = WiredEffectType.USERS_GROUP_SELECTOR;

    private int groupType = GROUP_CURRENT_ROOM;
    private int selectedGroupId = 0;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        int targetGroupId = this.resolveTargetGroupId(room);
        Set<RoomUnit> result = new LinkedHashSet<>();

        if (targetGroupId > 0) {
            for (User user : room.getUsers()) {
                if (user == null || user.getRoomUnit() == null || user.getUserStats() == null) {
                    continue;
                }

                if (user.getUserStats().hasGuild(targetGroupId)) {
                    result.add(user.getRoomUnit());
                }
            }
        }

        Set<RoomUnit> availableUsers = room.getUsers().stream()
                .filter(user -> user != null && user.getRoomUnit() != null)
                .map(User::getRoomUnit)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        result = this.applySelectorModifiers(
                result, availableUsers, ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        int groupId;
        try {
            String text = settings.getStringParam() == null
                    ? ""
                    : settings.getStringParam().trim();
            groupId = text.isEmpty() ? 0 : Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            groupId = 0;
        }
        this.selectedGroupId = groupId;
        this.groupType = groupId > 0 ? GROUP_SELECTED : GROUP_CURRENT_ROOM;
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
                .toJson(new JsonData(
                        this.groupType, this.selectedGroupId, this.filterExisting, this.invert, this.getDelay()));
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

        this.groupType = this.normalizeGroupType(data.groupType);
        this.selectedGroupId = Math.max(0, data.selectedGroupId);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.groupType = GROUP_CURRENT_ROOM;
        this.selectedGroupId = 0;
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
        // Official layout: the group id in the string (empty: the room's group), no int params.
        message.appendString(
                this.groupType == GROUP_SELECTED && this.selectedGroupId > 0
                        ? String.valueOf(this.selectedGroupId)
                        : "");
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

    private int resolveTargetGroupId(Room room) {
        if (this.groupType == GROUP_CURRENT_ROOM) {
            return room.getGuildId();
        }

        return this.selectedGroupId;
    }

    private int normalizeGroupType(int value) {
        return (value == GROUP_SELECTED) ? GROUP_SELECTED : GROUP_CURRENT_ROOM;
    }

    static class JsonData {
        int groupType;
        int selectedGroupId;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int groupType, int selectedGroupId, boolean filterExisting, boolean invert, int delay) {
            this.groupType = groupType;
            this.selectedGroupId = selectedGroupId;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
