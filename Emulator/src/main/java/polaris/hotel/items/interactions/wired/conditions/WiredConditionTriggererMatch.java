package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionTriggererMatch extends InteractionWiredCondition implements WiredOfficialQuantifier {
    protected static final int ENTITY_USER = 1;
    protected static final int ENTITY_PET = 2;
    protected static final int ENTITY_BOT = 4;
    protected static final int AVATAR_MODE_ANY = 0;
    protected static final int AVATAR_MODE_CERTAIN = 1;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int SOURCE_SPECIFIED_USERNAME = 101;
    protected static final int MAX_USERNAME_LENGTH = 64;

    public static final WiredConditionType type = WiredConditionType.TRIGGERER_MATCH;

    private int entityType = ENTITY_USER;
    private int avatarMode = AVATAR_MODE_ANY;
    private int matchUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int compareUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;
    private String username = "";

    public WiredConditionTriggererMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggererMatch(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        MatchResult result = this.evaluateMatch(ctx);
        return result.valid && result.matched;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.entityType,
                        this.avatarMode,
                        this.matchUserSource,
                        this.compareUserSource,
                        this.quantifier,
                        this.username));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException exception) {
            this.resetSettings();
            return;
        }

        if (data == null) {
            return;
        }

        this.entityType = this.normalizeEntityType(data.entityType);
        this.avatarMode = this.normalizeAvatarMode(data.avatarMode);
        this.matchUserSource = this.normalizePrimaryUserSource(data.matchUserSource);
        this.compareUserSource = this.normalizeCompareUserSource(data.compareUserSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);
        this.username = this.normalizeUsername(data.username);
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.username);
        // Official layout: [type mask (1 Habbo, 2 pet, 4 bot)], the name in the string (empty: any), the
        // users in the official source slot, the quantifier in its slot; our compare source follows.
        message.appendInt(2);
        message.appendInt(this.entityType);
        message.appendInt(this.compareUserSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.resetSettings();

        if (params.length > 0) this.entityType = this.normalizeEntityType(params[0]);
        if (params.length > 1) this.compareUserSource = this.normalizeCompareUserSource(params[1]);
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());
        if (settings.getUserSourceTypes().length > 0)
            this.matchUserSource = this.normalizePrimaryUserSource(settings.getUserSourceTypes()[0]);

        this.username = this.normalizeUsername(settings.getStringParam());
        // A name narrows the match to that name; without one any name matches.
        this.avatarMode = this.username.isEmpty() ? AVATAR_MODE_ANY : AVATAR_MODE_CERTAIN;

        return true;
    }

    protected MatchResult evaluateMatch(WiredContext ctx) {
        List<RoomUnit> matchUsers = this.resolvePrimaryUsers(ctx);
        if (matchUsers.isEmpty()) {
            return MatchResult.valid(WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY));
        }

        List<RoomUnit> compareUsers = this.resolveCompareUsers(ctx);
        if (compareUsers.isEmpty()) {
            return MatchResult.valid(false);
        }

        Set<Integer> compareUserIds = compareUsers.stream()
                .filter(this::matchesEntityType)
                .map(RoomUnit::getId)
                .collect(Collectors.toSet());

        if (compareUserIds.isEmpty()) {
            return MatchResult.valid(false);
        }

        boolean matched;
        if (this.quantifier == QUANTIFIER_ANY) {
            matched = matchUsers.stream().anyMatch(roomUnit -> this.matchesCandidate(roomUnit, compareUserIds));
        } else {
            matched = matchUsers.stream().allMatch(roomUnit -> this.matchesCandidate(roomUnit, compareUserIds));
        }

        return MatchResult.valid(matched);
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    private void resetSettings() {
        this.entityType = ENTITY_USER;
        this.avatarMode = AVATAR_MODE_ANY;
        this.matchUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.compareUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.username = "";
    }

    private List<RoomUnit> resolvePrimaryUsers(WiredContext ctx) {
        return this.deduplicate(WiredSourceUtil.resolveUsers(ctx, this.matchUserSource));
    }

    private List<RoomUnit> resolveCompareUsers(WiredContext ctx) {
        List<RoomUnit> resolved;

        if (this.compareUserSource == SOURCE_SPECIFIED_USERNAME) {
            resolved = this.resolveUsersByName(ctx.room(), this.username);
        } else {
            resolved = WiredSourceUtil.resolveUsers(ctx, this.compareUserSource);
        }

        if (this.avatarMode == AVATAR_MODE_CERTAIN) {
            String normalizedName = this.normalizeUsername(this.username);
            if (normalizedName.isEmpty()) {
                return new ArrayList<>();
            }

            resolved = resolved.stream()
                    .filter(roomUnit ->
                            namesMatch(normalizedName, this.getRoomUnitName(ctx.room(), roomUnit), roomUnit))
                    .collect(Collectors.toList());
        }

        return this.deduplicate(resolved);
    }

    private List<RoomUnit> resolveUsersByName(Room room, String username) {
        List<RoomUnit> result = new ArrayList<>();
        String normalizedName = this.normalizeUsername(username);
        if (room == null || normalizedName.isEmpty()) {
            return result;
        }

        User user = room.getUser(normalizedName);
        // A user's name matches with its case, as AIR does; bots and pets ignore it.
        if (user != null
                && user.getRoomUnit() != null
                && user.getUserInfo() != null
                && normalizedName.equals(user.getUserInfo().getUsername())) {
            result.add(user.getRoomUnit());
        }

        for (Bot bot : room.getBots(normalizedName)) {
            if (bot != null && bot.getRoomUnit() != null) {
                result.add(bot.getRoomUnit());
            }
        }

        for (Pet pet : room.getUnitManager().getPets()) {
            if (pet != null && pet.getRoomUnit() != null && normalizedName.equalsIgnoreCase(pet.getName())) {
                result.add(pet.getRoomUnit());
            }
        }

        return result;
    }

    private List<RoomUnit> deduplicate(List<RoomUnit> users) {
        Map<Integer, RoomUnit> deduplicated = new LinkedHashMap<>();

        for (RoomUnit user : users) {
            if (user != null) {
                deduplicated.putIfAbsent(user.getId(), user);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private boolean matchesCandidate(RoomUnit roomUnit, Set<Integer> compareUserIds) {
        return roomUnit != null && this.matchesEntityType(roomUnit) && compareUserIds.contains(roomUnit.getId());
    }

    private boolean matchesEntityType(RoomUnit roomUnit) {
        return roomUnit != null && (roomUnit.getRoomUnitType().getTypeId() & this.entityType) != 0;
    }

    /** A user's name is compared with its case, a bot's or pet's without, as AIR does. */
    static boolean namesMatch(String wanted, String name, RoomUnit roomUnit) {
        if (name == null) {
            return false;
        }

        return roomUnit != null && roomUnit.getRoomUnitType() == RoomUnitType.USER
                ? wanted.equals(name)
                : wanted.equalsIgnoreCase(name);
    }

    private String getRoomUnitName(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            User user = room.getUser(roomUnit);
            return (user != null && user.getUserInfo() != null)
                    ? user.getUserInfo().getUsername()
                    : "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
            Bot bot = room.getBot(roomUnit);
            return (bot != null) ? bot.getName() : "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.PET) {
            Pet pet = room.getPet(roomUnit);
            return (pet != null) ? pet.getName() : "";
        }

        return "";
    }

    /** The official type mask 1-7: any of Habbo (1), pet (2) and bot (4); anything else is Habbo. */
    int normalizeEntityType(int value) {
        return value >= ENTITY_USER && value <= (ENTITY_USER | ENTITY_PET | ENTITY_BOT) ? value : ENTITY_USER;
    }

    int normalizeAvatarMode(int value) {
        return (value == AVATAR_MODE_CERTAIN) ? AVATAR_MODE_CERTAIN : AVATAR_MODE_ANY;
    }

    int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    int normalizePrimaryUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    int normalizeCompareUserSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_CLICKED_USER:
            case SOURCE_SPECIFIED_USERNAME:
                return value;
            default:
                return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    String normalizeUsername(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() <= MAX_USERNAME_LENGTH ? normalized : normalized.substring(0, MAX_USERNAME_LENGTH);
    }

    protected static class MatchResult {
        protected final boolean valid;
        protected final boolean matched;

        private MatchResult(boolean valid, boolean matched) {
            this.valid = valid;
            this.matched = matched;
        }

        private static MatchResult invalid() {
            return new MatchResult(false, false);
        }

        private static MatchResult valid(boolean matched) {
            return new MatchResult(true, matched);
        }
    }

    static class JsonData {
        int entityType;
        int avatarMode;
        int matchUserSource;
        int compareUserSource;
        int quantifier;
        String username;

        public JsonData(
                int entityType,
                int avatarMode,
                int matchUserSource,
                int compareUserSource,
                int quantifier,
                String username) {
            this.entityType = entityType;
            this.avatarMode = avatarMode;
            this.matchUserSource = matchUserSource;
            this.compareUserSource = compareUserSource;
            this.quantifier = quantifier;
            this.username = username;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.getQuantifier();
    }
}
