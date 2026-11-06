package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredLargePayload;
import polaris.hotel.items.interactions.wired.WiredRewardPolicy;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.wired.WiredEnvironmentComposer;

/**
 * Habbo's achievement enabler add-on: names the achievements the room's wired may progress. A
 * progress-achievement box only works for an achievement an enabler in its room names, and only
 * when the hotel allows that achievement too. The room's names go to its visitors in
 * {@code WiredEnvironment}.
 *
 * <p>String param: the names, separated by commas, semicolons or white space, at most 2000
 * characters (the editor's limit, above the usual 1024). No int params.
 */
public class WiredExtraAchievementEnabler extends InteractionWiredExtra implements WiredLargePayload {
    public static final int CODE = 151;
    public static final int MAX_TEXT_LENGTH = 2000;
    public static final int MAX_NAMES = 50;
    public static final int MAX_NAME_LENGTH = 64;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1," + MAX_NAME_LENGTH + "}");

    private List<String> achievements = List.of();

    public WiredExtraAchievementEnabler(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraAchievementEnabler(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        this.achievements = parseNames(settings.getStringParam());
        refreshEnvironment(settings.getRoom());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(new ArrayList<>(this.achievements)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(String.join("\n", this.achievements));
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null && data.achievements != null) {
            Set<String> names = new LinkedHashSet<>();
            for (String name : data.achievements) {
                if (names.size() >= MAX_NAMES) {
                    break;
                }
                if (name != null && NAME.matcher(name).matches()) {
                    names.add(name);
                }
            }
            this.achievements = List.copyOf(names);
        }
    }

    @Override
    public void onPickUp() {
        this.achievements = List.of();
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        refreshEnvironment(room);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        refreshEnvironment(room);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    /** The achievement names this enabler declares, in the order they were written. */
    public List<String> getAchievements() {
        return this.achievements;
    }

    /** Every achievement the room's enablers declare. */
    public static Set<String> declaredAchievements(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return Set.of();
        }

        Set<String> declared = new LinkedHashSet<>();
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraAchievementEnabler enabler) {
                declared.addAll(enabler.getAchievements());
            }
        }
        return Collections.unmodifiableSet(declared);
    }

    /** Valid, distinct names from the text; anything past the length and count limits is ignored. */
    public static List<String> parseNames(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String bounded = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        Set<String> names = new LinkedHashSet<>();
        for (String name : bounded.split("[,;\\s]+")) {
            if (names.size() >= MAX_NAMES) {
                break;
            }
            if (NAME.matcher(name).matches()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static void refreshEnvironment(Room room) {
        if (room != null && room.isLoaded()) {
            room.sendComposer(new WiredEnvironmentComposer(room).compose());
        }
    }

    static class JsonData {
        List<String> achievements;

        JsonData(List<String> achievements) {
            this.achievements = achievements;
        }
    }
}
