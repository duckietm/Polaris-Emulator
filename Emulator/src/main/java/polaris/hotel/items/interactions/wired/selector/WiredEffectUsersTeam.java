package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeamColors;
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

public class WiredEffectUsersTeam extends InteractionWiredEffect implements WiredOfficialSelector {
    private static final int TEAM_ANY = 0;

    public static final WiredEffectType type = WiredEffectType.USERS_TEAM_SELECTOR;

    private int teamType = TEAM_ANY;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            if (this.matchesTeam(room, roomUnit)) {
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
        this.teamType = (params.length > 0) ? this.normalizeTeamType(params[0]) : TEAM_ANY;
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
                .toJson(new JsonData(this.teamType, this.filterExisting, this.invert, this.getDelay()));
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

        this.teamType = this.normalizeTeamType(data.teamType);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.teamType = TEAM_ANY;
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
        message.appendInt(this.teamType);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private int normalizeTeamType(int value) {
        if (value == TEAM_ANY) {
            return TEAM_ANY;
        }

        GameTeamColors teamColor = GameTeamColors.fromType(value);
        return (teamColor.type >= GameTeamColors.RED.type && teamColor.type <= GameTeamColors.YELLOW.type)
                ? teamColor.type
                : TEAM_ANY;
    }

    private boolean matchesTeam(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return false;
        }

        User user = room.getUser(roomUnit);
        if (user == null || user.getUserInfo() == null) {
            return false;
        }

        GamePlayer gamePlayer = user.getUserInfo().getGamePlayer();
        if (gamePlayer == null
                || gamePlayer.getTeamColor() == null
                || gamePlayer.getTeamColor() == GameTeamColors.NONE) {
            return false;
        }

        return (this.teamType == TEAM_ANY) || gamePlayer.getTeamColor().type == this.teamType;
    }

    static class JsonData {
        int teamType;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int teamType, boolean filterExisting, boolean invert, int delay) {
            this.teamType = teamType;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
