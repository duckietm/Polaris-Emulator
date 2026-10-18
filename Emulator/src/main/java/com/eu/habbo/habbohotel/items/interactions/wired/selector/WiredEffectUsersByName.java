package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSelector;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectUsersByName extends InteractionWiredEffect implements WiredOfficialSelector {
    static final int MAX_NAMES = 20;
    static final int MAX_NAMES_TEXT_LENGTH = 1000;
    public static final WiredEffectType type = WiredEffectType.USERS_BY_NAME_SELECTOR;

    private String namesText = "";
    private Set<String> usernames = new LinkedHashSet<>();
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersByName(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersByName(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        // Every avatar by its name, as officially: users, bots and pets.
        Set<RoomUnit> result = new LinkedHashSet<>();
        Set<RoomUnit> availableUsers = new LinkedHashSet<>();

        for (RoomUnit unit : room.getRoomUnits()) {
            if (unit == null) {
                continue;
            }
            availableUsers.add(unit);

            String name = nameOf(room, unit);
            if (name != null && this.usernames.contains(name.trim().toLowerCase(Locale.ROOT))) {
                result.add(unit);
            }
        }
        result = this.applySelectorModifiers(
                result, availableUsers, ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        String names = this.normalizeNamesText(settings.getStringParam());
        refuseOversizedNames(settings.getStringParam(), names);
        this.namesText = names;
        this.usernames = this.parseUsernames(this.namesText);
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
                .toJson(new JsonData(this.namesText, this.filterExisting, this.invert, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
            if (data == null) {
                return;
            }

            this.namesText = this.normalizeNamesText(data.namesText);
            this.filterExisting = data.filterExisting;
            this.invert = data.invert;
            this.setDelay(data.delay);
        } else {
            this.namesText = this.normalizeNamesText(wiredData);
        }

        this.usernames = this.parseUsernames(this.namesText);
    }

    @Override
    public void onPickUp() {
        this.namesText = "";
        this.usernames = new LinkedHashSet<>();
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
        message.appendString(this.namesText);
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

    private static String nameOf(Room room, RoomUnit unit) {
        Habbo habbo = room.getHabbo(unit);
        if (habbo != null && habbo.getHabboInfo() != null)
            return habbo.getHabboInfo().getUsername();
        var bot = room.getBot(unit);
        if (bot != null) return bot.getName();
        var pet = room.getPet(unit);
        return pet != null ? pet.getName() : null;
    }

    /**
     * The official editor takes at most 20 names and 1000 characters (UsersByName: TextAreaParam(140,-1,20,-1,1000)).
     * Behaviour observed in plus-emulator/PlusEMU b8f78f2a, reimplemented.
     */
    static void refuseOversizedNames(String raw, String normalized) {
        if (raw != null && raw.length() > MAX_NAMES_TEXT_LENGTH) {
            throw new WiredSaveRefusedException(
                    "wiredfurni.error.too_much_text", Map.of("limit", String.valueOf(MAX_NAMES_TEXT_LENGTH)));
        }
        if (!normalized.isEmpty() && normalized.split("\n").length > MAX_NAMES) {
            throw new WiredSaveRefusedException(
                    "wiredfurni.error.usernames_count", Map.of("max_usernames", String.valueOf(MAX_NAMES)));
        }
    }

    private String normalizeNamesText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        Set<String> normalizedLines = new LinkedHashSet<>();

        for (String line : value.split("[\\t\\r\\n]+")) {
            String normalized = line.trim();
            if (!normalized.isEmpty()) {
                normalizedLines.add(normalized);
            }
        }

        return normalizedLines.stream().collect(Collectors.joining("\n"));
    }

    private Set<String> parseUsernames(String value) {
        Set<String> result = new LinkedHashSet<>();

        if (value == null || value.trim().isEmpty()) {
            return result;
        }

        for (String line : value.split("[\\t\\r\\n]+")) {
            String normalized = line.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        return result;
    }

    static class JsonData {
        String namesText;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(String namesText, boolean filterExisting, boolean invert, int delay) {
            this.namesText = namesText;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
