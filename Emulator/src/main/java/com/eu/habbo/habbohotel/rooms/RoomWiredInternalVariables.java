package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Habbo's built-in wired variables as the official variable list carries them: a fixed id per variable (negative for
 * the internal ones, {@code ~} for the smart ones), the official name, target and flags. Only the variables our wired
 * engine reads are listed; a smart variable is sent invisible while nothing in the room is of its kind, as Habbo does.
 *
 * <p>The table follows a capture of Habbo's own list (WIN63, 2026-10-12). The value tables of the handitems, effects,
 * habbicons and chat bubbles are hotel content and are left out; the packed values ({@code @position}, {@code
 * @occupation}, the recolorable colour) carry an empty table, which makes the client show them as "x: 14, y: 22".
 */
public final class RoomWiredInternalVariables {

    /** Internal variables that Give Variable and Remove Variable can hand out and take away (a presence flag). */
    private static final Set<String> CREATE_AND_DELETE_KEYS =
            Set.of("@handitem_id", "@has_rights", "@can_move_freely", "@habbicon");

    private static final List<Row> ROWS = rows();

    /** The rows by their official id. */
    private static final Map<String, Row> BY_ID = byIdTable();

    /** The official id of each target's variables, by our registry key. */
    private static final Map<String, String> ID_BY_TARGET_KEY = idTable();

    private RoomWiredInternalVariables() {}

    /** The built-in variable of an official id ({@code -125}, {@code ~133}), or null when Habbo has none of it. */
    public static Row byId(String id) {
        return id == null ? null : BY_ID.get(id.trim());
    }

    /**
     * The official id of a built-in variable of the target (furni 0, user 1, global -10, context -20), by its name or
     * our registry key; null when Habbo lists no such variable for the target.
     */
    public static String idOf(int target, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return ID_BY_TARGET_KEY.get(target + "|" + WiredInternalVariableSupport.normalizeKey(key.trim()));
    }

    private static Map<String, Row> byIdTable() {
        Map<String, Row> table = new LinkedHashMap<>();
        for (Row row : ROWS) {
            table.put(row.id(), row);
        }
        return Collections.unmodifiableMap(table);
    }

    private static Map<String, String> idTable() {
        Map<String, String> table = new LinkedHashMap<>();
        for (Row row : ROWS) {
            table.putIfAbsent(row.target() + "|" + row.key(), row.id());
        }
        return Collections.unmodifiableMap(table);
    }

    /** One built-in variable as Habbo lists it. */
    public record Row(
            String id,
            String name,
            int target,
            boolean alwaysAvailable,
            boolean canCreateAndDelete,
            boolean hasValue,
            boolean canWriteValue,
            Map<Integer, String> textConnector) {

        /** True for {@code ~} variables, AIR's smart variables (variable type 3). */
        public boolean isSmart() {
            return this.id.startsWith("~");
        }

        /** Our registry's key of the variable ({@code @position.x} is our {@code @position_x}). */
        public String key() {
            return WiredInternalVariableSupport.normalizeKey(this.name);
        }
    }

    /** The whole official table, supported or not. */
    public static List<Row> officialRows() {
        return ROWS;
    }

    /** Whether our wired engine reads the variable for its target. */
    public static boolean isSupported(Row row) {
        String key = row.key();
        if (CREATE_AND_DELETE_KEYS.contains(key)) {
            return true;
        }
        return switch (row.target()) {
            case RoomWiredVariableCatalog.TARGET_FURNI -> WiredInternalVariableSupport.canUseFurniReference(key);
            case RoomWiredVariableCatalog.TARGET_USER -> WiredInternalVariableSupport.canUseUserReference(key);
            case RoomWiredVariableCatalog.TARGET_GLOBAL -> WiredInternalVariableSupport.canUseRoomReference(key);
            case RoomWiredVariableCatalog.TARGET_CONTEXT -> WiredInternalVariableSupport.canUseContextReference(key);
            default -> false;
        };
    }

    /** Whether a wired box can write the variable (Change Variable, the variable editor). */
    static boolean canWrite(Row row) {
        if (!row.hasValue()) {
            return false;
        }
        String key = row.key();
        return switch (row.target()) {
            case RoomWiredVariableCatalog.TARGET_FURNI -> WiredInternalVariableSupport.canUseFurniDestination(key);
            case RoomWiredVariableCatalog.TARGET_USER -> WiredInternalVariableSupport.canUseUserDestination(key);
            case RoomWiredVariableCatalog.TARGET_GLOBAL -> WiredInternalVariableSupport.canUseRoomDestination(key);
            default -> false;
        };
    }

    /**
     * Whether Variable Changed can watch the variable: a writable one and the rights flag, the ones our trigger
     * accepts. The projectile counts it also watches are not values a box writes, so they stay off here.
     */
    static boolean canIntercept(Row row) {
        return canWrite(row)
                || (row.target() == RoomWiredVariableCatalog.TARGET_USER && "@has_rights".equals(row.key()));
    }

    /** The catalog entries of every supported built-in variable of the room. */
    public static List<RoomWiredVariableCatalog.Variable> catalogEntries(Room room) {
        List<RoomWiredVariableCatalog.Variable> variables = new ArrayList<>();
        for (Row row : ROWS) {
            if (!isSupported(row)) {
                continue;
            }

            boolean writable = canWrite(row);
            variables.add(new RoomWiredVariableCatalog.Variable(
                    row.id(),
                    row.isSmart() ? RoomWiredVariableCatalog.TYPE_SMART : RoomWiredVariableCatalog.TYPE_INTERNAL,
                    row.name(),
                    0,
                    row.target(),
                    row.alwaysAvailable(),
                    CREATE_AND_DELETE_KEYS.contains(row.key()),
                    row.hasValue(),
                    writable,
                    canIntercept(row),
                    row.isSmart() && !anyHolder(room, row),
                    false,
                    false,
                    false,
                    row.textConnector() != null,
                    !writable,
                    row.textConnector(),
                    null));
        }
        return variables;
    }

    /** Whether anything in the room is of the smart variable's kind (a chest for {@code ~chest.*}, a pet, ...). */
    private static boolean anyHolder(Room room, Row row) {
        if (room == null) {
            return false;
        }
        String key = row.key();
        if (row.target() == RoomWiredVariableCatalog.TARGET_FURNI) {
            for (HabboItem item : room.getFloorItems()) {
                if (WiredInternalVariableSupport.hasFurniValue(item, key)) {
                    return true;
                }
            }
            return false;
        }
        for (Pet pet : room.getCurrentPets().values()) {
            if (pet != null && WiredInternalVariableSupport.hasUserValue(room, pet.getRoomUnit(), key)) {
                return true;
            }
        }
        return false;
    }

    private static List<Row> rows() {
        Table table = new Table();
        int furni = RoomWiredVariableCatalog.TARGET_FURNI;
        int user = RoomWiredVariableCatalog.TARGET_USER;
        int global = RoomWiredVariableCatalog.TARGET_GLOBAL;
        int context = RoomWiredVariableCatalog.TARGET_CONTEXT;

        table.add("-100", "@id", furni, "AV");
        table.add("-101", "@class_id", furni, "AV");
        table.add("-102", "@height", furni, "AV");
        table.add("-110", "@state", furni, "AVW");
        table.add("-120", "@position.x", furni, "AVW");
        table.add("-121", "@position.y", furni, "AVW");
        table.add("-122", "@rotation", furni, "AVW");
        table.add("-123", "@altitude", furni, "AVW");
        table.add("-124", "@is_invisible", furni, "");
        table.add("-125", "@position", furni, "AVW", packed());
        table.add("-126", "@occupation", furni, "AVW", packed());
        table.add("-129", "@type", furni, "AV", texts(0, "Normal", 1, "BC", 2, "Temp"));
        table.add("-130", "@is_stackable", furni, "");
        table.add("-131", "@can_stand_on", furni, "");
        table.add("-132", "@can_sit_on", furni, "");
        table.add("-133", "@can_lay_on", furni, "");
        table.add("-140", "@dimensions.x", furni, "AV");
        table.add("-141", "@dimensions.y", furni, "AV");
        table.add("-180", "@owner_id", furni, "AV");
        table.add("-190", "@wallitem_offset", furni, "VW");
        table.add("-2000", "@can_move_freely", furni, "C");
        table.add("-2100", "@projectile.animation.tiles_travelled", furni, "V");
        table.add("-2101", "@projectile.animation.user_collisions", furni, "V");
        table.add("-2102", "@projectile.animation.furni_collisions", furni, "V");
        table.add("-2103", "@projectile.animation.position.x", furni, "V");
        table.add("-2104", "@projectile.animation.position.y", furni, "V");
        table.add("-2105", "@projectile.animation.position.altitude", furni, "V");
        table.add("-2106", "@projectile.animation.is_travelling", furni, "");

        table.add("-200", "@index", user, "AV");
        table.add("-205", "@type", user, "AV", texts(1, "User", 2, "Pet", 3, "Old bot", 4, "Bot"));
        table.add("-206", "@gender", user, "AV", texts(-1, "Unknown", 0, "M", 1, "F"));
        table.add("-210", "@level", user, "AV");
        table.add("-211", "@achievement_score", user, "AV");
        table.add("-220", "@is_hc", user, "");
        table.add("-221", "@has_rights", user, "");
        table.add("-222", "@is_group_admin", user, "");
        table.add("-223", "@is_owner", user, "");
        table.add("-230", "@position.x", user, "AVW");
        table.add("-231", "@position.y", user, "AVW");
        table.add(
                "-232",
                "@direction",
                user,
                "AVW",
                texts(0, "N", 1, "NE", 2, "E", 3, "SE", 4, "S", 5, "SW", 6, "W", 7, "NW"));
        table.add("-233", "@altitude", user, "AV");
        table.add("-234", "@position", user, "AVW", packed());
        table.add("-237", "@team.score", user, "VW");
        table.add("-238", "@team.color", user, "V", texts(1, "Red", 2, "Green", 3, "Blue", 4, "Yellow"));
        table.add(
                "-239",
                "@team.type",
                user,
                "V",
                texts(0, "Battle Banzai", 1, "Freeze", 2, "Tagging", 3, "Swimming", 4, "Wired"));
        table.add("-240", "@handitem", user, "CVW");
        table.add("-241", "@effect", user, "V");
        table.add("-242", "@is_frozen", user, "");
        table.add("-243", "@is_muted", user, "");
        table.add("-244", "@is_trading", user, "");
        table.add("-245", "@habbicon", user, "CVW");
        table.add("-250", "@favourite_group_id", user, "V");
        table.add("-260", "@dance", user, "V", texts(1, "Dance", 2, "Pogo Mogo", 3, "Duck Funck", 4, "The Rollie"));
        table.add("-261", "@sign", user, "V");
        table.add("-262", "@is_idle", user, "");
        table.add("-266", "@room_entry.method", user, "AV", texts(0, "Default", 2, "Teleport", 3, "Room Network"));
        table.add("-267", "@room_entry.teleport_id", user, "V");
        table.add("-270", "@user_id", user, "V");
        table.add("-271", "@pet_id", user, "V");
        table.add("-272", "@bot_id", user, "V");
        table.add("-280", "@pet_owner_id", user, "V");
        table.add("-281", "@transaction.in_trade", user, "");
        table.add("-282", "@transaction.start_time", user, "V");
        table.add("-283", "@transaction.contract_id", user, "V");
        table.add("-284", "@transaction.state", user, "V", texts(1, "Adding Items", 2, "Confirmed"));
        table.add("-285", "@transaction.can_accept", user, "");
        table.add("-286", "@transaction.current_multiplier", user, "V");

        table.add("-300", "@furni_count", global, "AV");
        table.add("-301", "@user_count", global, "AV");
        table.add("-310", "@wired_timer", global, "AV");
        table.add("-320", "@teams.red.score", global, "AVW");
        table.add("-321", "@teams.green.score", global, "AVW");
        table.add("-322", "@teams.blue.score", global, "AVW");
        table.add("-323", "@teams.yellow.score", global, "AVW");
        table.add("-330", "@teams.red.size", global, "AV");
        table.add("-331", "@teams.green.size", global, "AV");
        table.add("-332", "@teams.blue.size", global, "AV");
        table.add("-333", "@teams.yellow.size", global, "AV");
        table.add("-380", "@room_id", global, "AV");
        table.add("-381", "@group_id", global, "AV");
        table.add("-1000", "@current_time", global, "AV");
        table.add("-1001", "@current_time.milliseconds_of_seconds", global, "AV");
        table.add("-1002", "@current_time.seconds_of_minute", global, "AV");
        table.add("-1003", "@current_time.minute_of_hour", global, "AV");
        table.add("-1004", "@current_time.hour_of_day", global, "AV");
        table.add(
                "-1005",
                "@current_time.day_of_week",
                global,
                "AV",
                texts(
                        1,
                        "Monday",
                        2,
                        "Tuesday",
                        3,
                        "Wednesday",
                        4,
                        "Thursday",
                        5,
                        "Friday",
                        6,
                        "Saturday",
                        7,
                        "Sunday"));
        table.add("-1006", "@current_time.day_of_month", global, "AV");
        table.add("-1007", "@current_time.day_of_year", global, "AV");
        table.add("-1008", "@current_time.week_of_year", global, "AV");
        table.add(
                "-1009",
                "@current_time.month_of_year",
                global,
                "AV",
                texts(
                        1,
                        "January",
                        2,
                        "February",
                        3,
                        "March",
                        4,
                        "April",
                        5,
                        "May",
                        6,
                        "June",
                        7,
                        "July",
                        8,
                        "August",
                        9,
                        "September",
                        10,
                        "October",
                        11,
                        "November",
                        12,
                        "December"));
        table.add("-1010", "@current_time.year", global, "AV");

        table.add("-400", "@selector_furni_count", context, "AV");
        table.add("-401", "@selector_user_count", context, "AV");
        table.add("-410", "@signal_furni_count", context, "AV");
        table.add("-411", "@signal_user_count", context, "AV");
        table.add("-420", "@event.signal.antenna_id", context, "V");
        table.add("-450", "@event.chat.type", context, "V", texts(0, "Talk", 1, "Shout", 2, "Whisper"));
        table.add("-451", "@event.chat.style", context, "V");
        table.add("-452", "@event.habbicon", context, "V");
        table.add("-453", "@event.link.source_room_id", context, "V");
        table.add("-459", "@event.variable_update.box_id", context, "V");
        table.add(
                "-460",
                "@event.variable_update.change_type",
                context,
                "V",
                texts(0, "Created", 1, "Value changed", 2, "Deleted"));
        table.add("-461", "@event.variable_update.old_value", context, "V");
        table.add("-462", "@event.variable_update.new_value", context, "V");
        table.add("-463", "@event.variable_update.difference", context, "V");
        table.add(
                "-464",
                "@event.variable_update.change_origin",
                context,
                "V",
                texts(0, "In Room", 1, "Another Room", 2, "Inspection", 3, "External"));
        table.add("-470", "@event.transaction_complete.multiplier", context, "V");
        table.add("-471", "@event.transaction_complete.deposit.furni_count", context, "V");
        table.add("-472", "@event.transaction_complete.deposit.coins_count", context, "V");
        table.add("-473", "@event.transaction_complete.withdrawal.furni_count", context, "V");
        table.add("-474", "@event.transaction_complete.withdrawal.coins_count", context, "V");
        table.add("-478", "@event.transaction_failed.reason", context, "V");

        table.add("~100", "~background_color.hue", furni, "VW");
        table.add("~101", "~background_color.saturation", furni, "VW");
        table.add("~102", "~background_color.lightness", furni, "VW");
        table.add("~103", "~teleport.target_id", furni, "VW");
        table.add("~104", "~walk_helper.is_multi_walk", furni, "C");
        table.add("~110", "~area_hide.root_x", furni, "VW");
        table.add("~111", "~area_hide.root_y", furni, "VW");
        table.add("~112", "~area_hide.width", furni, "VW");
        table.add("~113", "~area_hide.length", furni, "VW");
        table.add("~114", "~area_hide.is_invisible_furni", furni, "C");
        table.add("~115", "~area_hide.hiding_wallitems", furni, "C");
        table.add("~116", "~area_hide.inverted", furni, "C");
        table.add("~120", "~clock.state", furni, "V", texts(0, "Initial", 1, "Running", 2, "Paused"));
        table.add("~121", "~clock.pulse_count", furni, "VW");
        table.add("~122", "~clock.is_game_aware", furni, "");
        table.add("~130", "~chest.available_amount", furni, "V");
        table.add("~131", "~chest.locked", furni, "");
        table.add("~132", "~chest.is_auto_lock", furni, "");
        table.add("~133", "~chest.capacity", furni, "V");
        table.add("~134", "~chest.is_open", furni, "");
        table.add("~135", "~chest.is_donatable", furni, "");
        table.add("~140", "~recolorable_furni.color.rgb.red", furni, "V");
        table.add("~141", "~recolorable_furni.color.rgb.green", furni, "V");
        table.add("~142", "~recolorable_furni.color.rgb.blue", furni, "V");
        table.add("~146", "~recolorable_furni.color.rgb", furni, "VW", packed());

        table.add("~400", "~pet.level", user, "V");
        table.add("~401", "~pet.experience", user, "V");
        table.add("~402", "~pet.energy", user, "V");
        table.add("~403", "~pet.happiness", user, "V");
        table.add("~410", "~pet.max_level", user, "V");
        table.add("~411", "~pet.experience_required", user, "V");
        table.add("~412", "~pet.max_energy", user, "V");
        table.add("~413", "~pet.max_happiness", user, "V");
        table.add("~420", "~pet.scratches", user, "V");
        table.add("~430", "~pet.owner_id", user, "V");
        table.add("~431", "~pet.creation_time", user, "V");
        table.add("~500", "~horse.has_saddle", user, "");
        table.add("~501", "~horse.is_riding", user, "");
        table.add("~502", "~horse.controller_user_id", user, "V");
        table.add("~600", "~plant.rarity_level", user, "V");
        table.add("~601", "~plant.shape", user, "V");
        table.add("~602", "~plant.color", user, "V");
        table.add("~610", "~plant.can_breed", user, "");
        table.add("~611", "~plant.can_harvest", user, "");
        table.add("~612", "~plant.can_revive", user, "");
        table.add("~613", "~plant.is_dead", user, "");
        table.add("~614", "~plant.is_growing", user, "");
        table.add("~615", "~plant.remaining_wellbeing_seconds", user, "V");
        table.add("~616", "~plant.remaining_growing_seconds", user, "V");
        table.add("~617", "~plant.max_wellbeing_seconds", user, "V");
        return Collections.unmodifiableList(table.rows);
    }

    /** An empty table: the value is packed, and the client unpacks it for display. */
    private static Map<Integer, String> packed() {
        return Collections.emptyMap();
    }

    private static Map<Integer, String> texts(Object... pairs) {
        Map<Integer, String> texts = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            texts.put((Integer) pairs[index], (String) pairs[index + 1]);
        }
        return Collections.unmodifiableMap(texts);
    }

    private static final class Table {
        private final List<Row> rows = new ArrayList<>();

        void add(String id, String name, int target, String flags) {
            this.add(id, name, target, flags, null);
        }

        /** {@code flags}: A alwaysAvailable, C canCreateAndDelete, V hasValue, W canWriteValue. */
        void add(String id, String name, int target, String flags, Map<Integer, String> textConnector) {
            this.rows.add(new Row(
                    id,
                    name,
                    target,
                    flags.indexOf('A') >= 0,
                    flags.indexOf('C') >= 0,
                    flags.indexOf('V') >= 0,
                    flags.indexOf('W') >= 0,
                    textConnector));
        }
    }
}
