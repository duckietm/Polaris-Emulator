package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayFormat;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class WiredExtraVariableTextConnector extends InteractionWiredExtra {
    public static final int CODE = 79;
    public static final int MAX_MAPPING_LENGTH = 1000;
    public static final int MAX_MAPPING_LINES = 30;
    private static final String PRESERVED_SPACE = "\u00A0";
    private static final Pattern VALID_KEY = Pattern.compile("-?[0-9]+");

    private String mappingsText = "";
    private int fieldId;
    private LinkedHashMap<Long, String> longMappings = new LinkedHashMap<>();
    /** The text of the official "_=text" line, shown for a value without a line of its own. */
    private String fallbackText = null;

    public WiredExtraVariableTextConnector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableTextConnector(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official: the "value=text" lines as plain text; ours follows: [array field id].
        ConfigData config = parseConfigData(settings.getStringParam());
        if (settings.getIntParams().length > 0) {
            config.fieldId = settings.getIntParams()[0];
        }
        String mappingsText = normalizeMappingsText(config.mappingsText);
        validateMappingsText(mappingsText);
        // As in Habbo, only the valid lines are kept, one per number in number order.
        mappingsText = storedMappingsText(mappingsText);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        validateField(room, config.fieldId);
        this.fieldId = Math.max(0, config.fieldId);
        this.setMappingsText(mappingsText);
        if (room != null) {
            WiredContextVariableSupport.broadcastDefinitions(room);
        }

        return true;
    }

    @Override
    public String getWiredData() {
        if (this.fieldId == 0) {
            return WiredManager.getGson().toJson(new LegacyJsonData(this.mappingsText));
        }
        return WiredManager.getGson().toJson(new JsonData(this.mappingsText, this.fieldId));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.mappingsText);
        message.appendInt(1);
        message.appendInt(this.fieldId);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.setMappingsText(data.mappingsText);
                this.fieldId = Math.max(0, data.fieldId);
            }

            return;
        }

        this.setMappingsText(wiredData);
    }

    @Override
    public void onPickUp() {
        this.mappingsText = "";
        this.fallbackText = null;
        this.fieldId = 0;
        this.longMappings = new LinkedHashMap<>();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getMappingsText() {
        return this.mappingsText;
    }

    public Map<Integer, String> getMappings() {
        LinkedHashMap<Integer, String> compatibleMappings = new LinkedHashMap<>();
        this.longMappings.forEach((key, value) -> {
            if (key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE) {
                compatibleMappings.put(key.intValue(), value);
            }
        });
        return Collections.unmodifiableMap(compatibleMappings);
    }

    public int getFieldId() {
        return this.fieldId;
    }

    public boolean appliesToField(int requestedFieldId) {
        return this.fieldId == Math.max(0, requestedFieldId);
    }

    public boolean appliesToField(WiredArrayVariableDefinition definition, int requestedFieldId) {
        if (definition == null || !definition.isArray()) return this.appliesToField(requestedFieldId);
        if (definition.getArrayDefinition().getFormat() == WiredArrayFormat.SIMPLE
                && requestedFieldId == WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID) {
            return this.fieldId == 0 || this.fieldId == WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
        }
        return this.appliesToField(requestedFieldId);
    }

    public String resolveText(Integer value) {
        if (value == null) {
            return "";
        }

        return this.resolveText(value.longValue());
    }

    public String resolveText(long value) {
        if (this.longMappings.containsKey(value)) {
            String mappedValue = this.longMappings.get(value);
            return mappedValue != null ? preserveSpaces(mappedValue) : "";
        }

        return String.valueOf(value);
    }

    public Integer resolveValue(String text) {
        if (text == null) {
            return null;
        }

        Long value = this.resolveLongValue(text);
        return value != null && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? value.intValue() : null;
    }

    public Long resolveLongValue(String text) {
        if (text == null) return null;
        String normalizedText = normalizePreservedSpaces(text);
        // Case-insensitive, and the last matching line wins, as in Habbo: "abc" against
        // 1=abc and 2=ABC reads 2.
        Long matched = null;

        for (Map.Entry<Long, String> entry : this.longMappings.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String normalizedMappingValue = normalizePreservedSpaces(entry.getValue());

            if (normalizedMappingValue.equalsIgnoreCase(normalizedText)) {
                matched = entry.getKey();
            }
        }

        return matched;
    }

    private void setMappingsText(String value) {
        this.mappingsText = normalizeMappingsText(value);
        this.longMappings = parseMappings(this.mappingsText);
        this.fallbackText = parseFallback(this.mappingsText);
    }

    /** The text of the box's "_=" line (spaces preserved), or null when it has none. */
    public String getFallbackText() {
        return this.fallbackText == null ? null : preserveSpaces(this.fallbackText);
    }

    private static String parseFallback(String value) {
        String fallback = null;
        for (String line : value.split("\n")) {
            int index = line.indexOf('=');
            if (index <= 0 || !line.substring(0, index).trim().equals("_")) continue;
            String label = line.substring(index + 1).trim();
            if (!label.isEmpty()) fallback = label;
        }
        return fallback;
    }

    private static String normalizeMappingsText(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\r", "");
    }

    private static void validateMappingsText(String value) throws WiredSaveException {
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.length() > MAX_MAPPING_LENGTH) {
            throw new WiredSaveException("Variable text connector can contain at most 1000 characters.");
        }

        int lineCount = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                lineCount++;
            }
        }

        if (lineCount > MAX_MAPPING_LINES) {
            throw new WiredSaveException("Variable text connector can contain at most 30 lines.");
        }
    }

    private static LinkedHashMap<Long, String> parseMappings(String value) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) {
            return result;
        }

        for (String rawLine : value.split("\n")) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine;
            if (line.trim().isEmpty()) {
                continue;
            }

            // As in Habbo, a line is "number=text" exactly: no spaces around the number, no other
            // separator; anything else is dropped ("bad", "x=1", " 3 = z "). The text may be empty.
            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String keyPart = line.substring(0, separatorIndex);
            String valuePart = line.substring(separatorIndex + 1);
            if (!VALID_KEY.matcher(keyPart).matches()) {
                continue;
            }

            try {
                // A repeated number keeps its last text.
                result.put(Long.parseLong(keyPart), valuePart);
            } catch (NumberFormatException ignored) {
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.EXTRA_TEXT_CONNECTOR_INDEX, ignored);
            }
        }

        return result;
    }

    /**
     * The text a saved connector stores, as Habbo does: its valid "number=text" lines only, one per
     * number with its last text, in ascending number order ("9=a 3=b -2=neg" reads back
     * "-2=neg 3=b 9=a"). The "_=" fallback line is kept, last.
     */
    static String storedMappingsText(String value) {
        StringBuilder stored = new StringBuilder();
        new TreeMap<>(parseMappings(value)).forEach((key, text) -> {
            if (!stored.isEmpty()) stored.append('\n');
            stored.append(key).append('=').append(text);
        });
        String fallback = parseFallback(value == null ? "" : value);
        if (fallback != null) {
            if (!stored.isEmpty()) stored.append('\n');
            stored.append("_=").append(fallback);
        }
        return stored.toString();
    }

    private static String preserveSpaces(String value) {
        return value.replace(" ", PRESERVED_SPACE);
    }

    private static String normalizePreservedSpaces(String value) {
        return value.replace(PRESERVED_SPACE, " ");
    }

    private static ConfigData parseConfigData(String value) {
        if (value == null || !value.trim().startsWith("{")) return new ConfigData(value, 0);
        ConfigData data = WiredExtraPayloadGuard.fromJson(value, ConfigData.class);
        return data == null ? new ConfigData("", 0) : data;
    }

    private void validateField(Room room, int requestedFieldId) throws WiredSaveException {
        if (requestedFieldId < 0) throw new WiredSaveException("Array connector field is invalid.");
        if (requestedFieldId == 0) return;
        if (room == null || room.getRoomSpecialTypes() == null) {
            throw new WiredSaveException("Array connector field is unavailable.");
        }
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(this.getX(), this.getY())) {
            if (extra instanceof WiredArrayVariableDefinition definition
                    && definition.isArray()
                    && definition.getArrayDefinition().getField(requestedFieldId) != null) return;
        }
        throw new WiredSaveException("Array connector field is not present on this stack.");
    }

    static class ConfigData {
        String mappingsText;
        int fieldId;

        ConfigData() {}

        ConfigData(String mappingsText, int fieldId) {
            this.mappingsText = mappingsText;
            this.fieldId = fieldId;
        }
    }

    static class JsonData extends ConfigData {
        JsonData(String mappingsText, int fieldId) {
            super(mappingsText, fieldId);
        }
    }

    static final class LegacyJsonData {
        String mappingsText;

        LegacyJsonData(String mappingsText) {
            this.mappingsText = mappingsText;
        }
    }
}
