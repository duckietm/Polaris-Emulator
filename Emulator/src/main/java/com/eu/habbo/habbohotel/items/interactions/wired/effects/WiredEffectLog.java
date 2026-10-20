package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.messages.ServerMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Habbo's "write to logs" action ({@code wf_act_log}): writes its message into the room's wired log
 * at the level it was set to (AIR's 0 info, 1 warn, 2 error, 3 debug), where the room log window
 * lists it under the "Wired" source. Username placeholders are filled in, and the users of its source
 * are named after the line.
 *
 * <p>Int params: {@code [log level, user source]}. String param: the message, at most 400 characters.
 */
public class WiredEffectLog extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectLog.class);
    public static final WiredEffectType type = WiredEffectType.WRITE_TO_LOGS;

    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_WARN = 1;
    public static final int LEVEL_ERROR = 2;
    public static final int LEVEL_DEBUG = 3;
    public static final int MAX_MESSAGE_LENGTH = 400;
    private static final int MAX_NAMED_USERS = 10;
    private static final int MAX_LINE_LENGTH = 1000;
    private static final Pattern STORED_LEVEL = Pattern.compile("([{,]\\s*)\"logLevel\"\\s*:\\s*(-?\\d+)");

    private String message = "";
    private int logLevel = LEVEL_INFO;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectLog(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectLog(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(1);
        message.appendInt(this.logLevel);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String message =
                (settings.getStringParam() != null) ? settings.getStringParam().trim() : "";
        if (message.isEmpty()) {
            return false;
        }
        this.message = (message.length() > MAX_MESSAGE_LENGTH) ? message.substring(0, MAX_MESSAGE_LENGTH) : message;

        int[] params = settings.getIntParams();
        this.logLevel = normalizeLevel((params.length > 0) ? params[0] : LEVEL_INFO);
        // Official layout: [level]; the user source is the official source slot.
        this.userSource =
                WiredVariableOperand.normalizeUserSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        String line = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, this.message) + describeUsers(ctx);
        // Placeholders can grow the line; the log keeps a bounded one.
        if (line.length() > MAX_LINE_LENGTH) {
            line = line.substring(0, MAX_LINE_LENGTH);
        }

        WiredManager.noteWiredLog(
                ctx.room().getId(),
                WiredRoomDiagnostics.Severity.fromLogLevel(this.logLevel),
                line,
                this.getBaseItem().getName(),
                this.getId());
        LOGGER.debug("[WiredLog room {}] {}", ctx.room().getId(), line);
    }

    public int getLogLevel() {
        return this.logLevel;
    }

    /** One of AIR's four levels; anything else reads as info, the dropdown's first entry. */
    static int normalizeLevel(int level) {
        return (level >= LEVEL_INFO && level <= LEVEL_DEBUG) ? level : LEVEL_INFO;
    }

    /**
     * A level stored before the levels followed AIR (0 debug, 1 info, 2 warning, 3 error) as AIR's
     * number (0 info, 1 warn, 2 error, 3 debug). Only the data migration uses it.
     */
    public static int legacyLevelToAir(int legacyLevel) {
        return switch (legacyLevel) {
            case 0 -> LEVEL_DEBUG;
            case 2 -> LEVEL_WARN;
            case 3 -> LEVEL_ERROR;
            default -> LEVEL_INFO;
        };
    }

    /**
     * A stored box's {@code wired_data} with its level moved from the old numbers to AIR's, or
     * {@code null} when nothing changes: the legacy tab format and a JSON box without a level both
     * read as info already, and anything that is not this box's JSON is left alone. Only the level's
     * digits are rewritten, so the message and every other field stay as they were stored.
     */
    public static String migrateLegacyLevel(String wiredData) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return null;
        }

        int stored;
        try {
            JsonElement level =
                    JsonParser.parseString(wiredData).getAsJsonObject().get("logLevel");
            if (level == null
                    || !level.isJsonPrimitive()
                    || !level.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            stored = level.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }

        // A quote inside a string value is always escaped, so an unescaped {"logLevel": or
        // ,"logLevel": can only be the key itself.
        Matcher matcher = STORED_LEVEL.matcher(wiredData);
        if (!matcher.find()) {
            return null;
        }

        return wiredData.substring(0, matcher.start(2))
                + legacyLevelToAir(stored)
                + wiredData.substring(matcher.end(2));
    }

    /**
     * The dialog carries a user source and the log line never mentioned who. It names them now, so a
     * line written for "the triggering user" can be told apart from one written for the whole room.
     * Empty when the source resolves to nobody, which keeps a log line that never needed a user clean.
     */
    private String describeUsers(WiredContext ctx) {
        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (users.isEmpty()) {
            return "";
        }

        Room room = ctx.room();
        StringBuilder names = new StringBuilder();
        int named = 0;

        for (RoomUnit unit : users) {
            if (named++ >= MAX_NAMED_USERS) {
                names.append(" +").append(users.size() - MAX_NAMED_USERS);
                break;
            }

            Habbo habbo = (room != null) ? room.getHabbo(unit) : null;
            String name = (habbo != null && habbo.getHabboInfo() != null)
                    ? habbo.getHabboInfo().getUsername()
                    : ("#" + unit.getId());

            names.append(names.isEmpty() ? "" : ", ").append(name);
        }

        return " [" + names + "]";
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.message, this.getDelay(), this.userSource, this.logLevel));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.message = data.message;
            this.setDelay(data.delay);
            this.userSource = data.userSource;
            // Boxes saved before the level existed wrote info-like lines, so they read as info.
            this.logLevel = normalizeLevel((data.logLevel != null) ? data.logLevel : LEVEL_INFO);
        } else {
            String[] data = wiredData.split("\t");
            this.message = "";

            if (data.length >= 2) {
                super.setDelay(Integer.parseInt(data[0]));

                try {
                    this.message = data[1];
                } catch (Exception e) {
                    WiredCompatibilityDiagnostics.record(
                            WiredCompatibilityDiagnostics.FailurePoint.EFFECT_LOG_LEGACY,
                            this.getRoomId(),
                            this.getId(),
                            e);
                }
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.logLevel = LEVEL_INFO;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    static class JsonData {
        String message;
        int delay;
        int userSource;
        Integer logLevel;

        public JsonData(String message, int delay, int userSource, int logLevel) {
            this.message = message;
            this.delay = delay;
            this.userSource = userSource;
            this.logLevel = logLevel;
        }
    }
}
