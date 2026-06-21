package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectUsersByName extends InteractionWiredEffect {
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

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getHabboInfo() == null || habbo.getRoomUnit() == null) {
                continue;
            }

            String username = habbo.getHabboInfo().getUsername();
            if (username == null) {
                continue;
            }

            if (this.usernames.contains(username.trim().toLowerCase(Locale.ROOT))) {
                result.add(habbo.getRoomUnit());
            }
        }

        Set<RoomUnit> availableUsers = room.getHabbos().stream()
                .filter(habbo -> habbo != null && habbo.getRoomUnit() != null)
                .map(Habbo::getRoomUnit)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        result = this.applySelectorModifiers(result, availableUsers, ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.namesText = this.normalizeNamesText(settings.getStringParam());
        this.usernames = this.parseUsernames(this.namesText);
        this.filterExisting = params.length > 0 && params[0] == 1;
        this.invert = params.length > 1 && params[1] == 1;
        this.setDelay(settings.getDelay());
        return true;
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
        return WiredManager.getGson().toJson(new JsonData(this.namesText, this.filterExisting, this.invert, this.getDelay()));
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
        message.appendInt(2);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private String normalizeNamesText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        Set<String> normalizedLines = new LinkedHashSet<>();

        for (String line : value.split("\\R")) {
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

        for (String line : value.split("\\R")) {
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
