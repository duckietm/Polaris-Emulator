package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredConditionTriggererMatch extends InteractionWiredCondition {
    protected static final int ENTITY_HABBO = 1;
    protected static final int ENTITY_PET = 2;
    protected static final int ENTITY_BOT = 4;
    protected static final int AVATAR_MODE_ANY = 0;
    protected static final int AVATAR_MODE_CERTAIN = 1;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;
    protected static final int SOURCE_SPECIFIED_USERNAME = 101;
    protected static final int MAX_USERNAME_LENGTH = 64;

    public static final WiredConditionType type = WiredConditionType.TRIGGERER_MATCH;

    private int entityType = ENTITY_HABBO;
    private int avatarMode = AVATAR_MODE_ANY;
    private int matchUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int compareUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;
    private String username = "";

    public WiredConditionTriggererMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggererMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        return WiredManager.getGson().toJson(new JsonData(
                this.entityType,
                this.avatarMode,
                this.matchUserSource,
                this.compareUserSource,
                this.quantifier,
                this.username
        ));
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
        message.appendInt(5);
        message.appendInt(this.entityType);
        message.appendInt(this.avatarMode);
        message.appendInt(this.matchUserSource);
        message.appendInt(this.compareUserSource);
        message.appendInt(this.quantifier);
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
        if (params.length > 1) this.avatarMode = this.normalizeAvatarMode(params[1]);
        if (params.length > 2) this.matchUserSource = this.normalizePrimaryUserSource(params[2]);
        if (params.length > 3) this.compareUserSource = this.normalizeCompareUserSource(params[3]);
        if (params.length > 4) this.quantifier = this.normalizeQuantifier(params[4]);

        this.username = this.normalizeUsername(settings.getStringParam());

        return true;
    }

    protected MatchResult evaluateMatch(WiredContext ctx) {
        List<RoomUnit> matchUsers = this.resolvePrimaryUsers(ctx);
        if (matchUsers.isEmpty()) {
            return MatchResult.invalid();
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
        this.entityType = ENTITY_HABBO;
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
                    .filter(roomUnit -> normalizedName.equalsIgnoreCase(this.getRoomUnitName(ctx.room(), roomUnit)))
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

        Habbo habbo = room.getHabbo(normalizedName);
        if (habbo != null && habbo.getRoomUnit() != null) {
            result.add(habbo.getRoomUnit());
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
        return roomUnit != null && roomUnit.getRoomUnitType().getTypeId() == this.entityType;
    }

    private String getRoomUnitName(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);
            return (habbo != null && habbo.getHabboInfo() != null) ? habbo.getHabboInfo().getUsername() : "";
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

    int normalizeEntityType(int value) {
        switch (value) {
            case ENTITY_HABBO:
            case ENTITY_PET:
            case ENTITY_BOT:
                return value;
            default:
                return ENTITY_HABBO;
        }
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

        public JsonData(int entityType, int avatarMode, int matchUserSource, int compareUserSource, int quantifier, String username) {
            this.entityType = entityType;
            this.avatarMode = avatarMode;
            this.matchUserSource = matchUserSource;
            this.compareUserSource = compareUserSource;
            this.quantifier = quantifier;
            this.username = username;
        }
    }
}
