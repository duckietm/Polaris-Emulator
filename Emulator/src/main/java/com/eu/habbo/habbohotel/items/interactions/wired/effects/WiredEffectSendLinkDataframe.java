package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import com.eu.habbo.habbohotel.wired.link.WiredLinkDataframeCodec;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Official send link dataframe (64, ours 171, {@code wf_act_send_dataframe}, AIR {@code SendLinkDataframe} /
 * {@code LinkDataframeSendPreset}): sends up to 12 named values through the picked WIRED Links.
 *
 * <p>Int params {@code [rowCount, 12 x (mode, literal, target)]}: mode 0 a typed number (literal), 1 a variable's
 * value, 2 a variable's text (its text connector), 3 a custom message (the row's text, at most 200 bytes, with
 * placeholders); target the official variable target (0 furni, 1 user, -10 global, -20 context). String param
 * {@code [[key, text], ...]}, 12 variable ids. Furni source slot 0 is the Links; row i reads its holders from furni
 * slot i + 1 and user slot i (AIR mergedSelections).
 */
public class WiredEffectSendLinkDataframe extends WiredEffectSendLinkBase
        implements WiredOfficialVariableIds, WiredOfficialSecondaryPicks {
    public static final WiredEffectType type = WiredEffectType.SEND_LINK_DATAFRAME;
    private static final int ROWS = WiredLinkDataframeCodec.MAX_ROWS;

    private List<Row> rows = List.of();
    private final Set<HabboItem> sourceFurni = new LinkedHashSet<>();

    /** One row with the holders its variable is read from. */
    record Row(
            String key,
            int mode,
            int literal,
            int target,
            String text,
            String variableId,
            int furniSource,
            int userSource) {}

    public WiredEffectSendLinkDataframe(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSendLinkDataframe(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected WiredLinkPayload.Kind kind() {
        return WiredLinkPayload.Kind.DATAFRAME;
    }

    @Override
    protected WiredLinkPayload payload(WiredContext ctx) {
        Room room = ctx.room();
        Map<String, String> values = new LinkedHashMap<>();
        for (Row row : this.rows) {
            String value = this.value(ctx, room, row);
            if (value != null) values.put(row.key(), value);
        }
        return WiredLinkPayload.dataframe(values);
    }

    private String value(WiredContext ctx, Room room, Row row) {
        switch (row.mode()) {
            case WiredLinkDataframeCodec.NUMBER_LITERAL:
                return Integer.toString(row.literal());
            case WiredLinkDataframeCodec.CUSTOM_MESSAGE:
                return WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, row.text());
            case WiredLinkDataframeCodec.NUMBER_VARIABLE:
            case WiredLinkDataframeCodec.VARIABLE_TEXT: {
                String token = WiredVariableOperand.normalizeToken(row.variableId());
                if (token.isEmpty()) {
                    return row.mode() == WiredLinkDataframeCodec.VARIABLE_TEXT ? "" : null;
                }
                Integer value = WiredVariableOperand.read(
                        ctx,
                        room,
                        operandTarget(row.target()),
                        token,
                        row.userSource(),
                        row.furniSource(),
                        new ArrayList<>(this.sourceFurni));
                if (value == null) return null;
                if (row.mode() == WiredLinkDataframeCodec.NUMBER_VARIABLE) return Integer.toString(value);
                int definitionId = WiredLinkDataframeCodec.parseVariableId(row.variableId());
                String text =
                        definitionId > 0 ? WiredVariableTextConnectorSupport.toText(room, definitionId, value) : null;
                return text != null ? text : Integer.toString(value);
            }
            default:
                return null;
        }
    }

    /** The official variable target as WiredVariableOperand numbers it. */
    static int operandTarget(int officialTarget) {
        return switch (officialTarget) {
            case 0 -> WiredVariableOperand.TARGET_FURNI;
            case -10 -> WiredVariableOperand.TARGET_ROOM;
            case -20 -> WiredVariableOperand.TARGET_CONTEXT;
            default -> WiredVariableOperand.TARGET_USER;
        };
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        String stringParam = settings.getStringParam() == null ? "" : settings.getStringParam();
        String[] variableIds = WiredLinkDataframeCodec.twelveVariableIds(settings.getVariableIds());
        if (intParams.length != 1 + 3 * ROWS) {
            throw new WiredSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
        }
        int count = intParams[0];
        List<WiredLinkDataframeCodec.Row> decoded = WiredLinkDataframeCodec.decode(stringParam, count);
        if (decoded == null) {
            throw new WiredSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
        }
        String sizeError = WiredLinkDataframeCodec.storageError(stringParam, intParams, variableIds);
        if (sizeError != null) throw new WiredSaveException(sizeError);

        Set<String> keys = new HashSet<>();
        List<Row> next = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            WiredLinkDataframeCodec.Row stored = decoded.get(i);
            int mode = intParams[1 + i * 3];
            int literal = intParams[2 + i * 3];
            int target = intParams[3 + i * 3];
            String keyError = WiredLinkDataframeCodec.keyError(stored.key());
            if (keyError != null) throw new WiredSaveException(keyError);
            if (mode < 0 || mode > 3 || !WiredLinkDataframeCodec.isTarget(target)) {
                throw new WiredSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
            }
            String key = WiredLinkDataframeCodec.normalizeKey(stored.key());
            if (!keys.add(key)) throw new WiredSaveException(WiredLinkDataframeCodec.NAME_UNIQUE);
            String text = mode == WiredLinkDataframeCodec.CUSTOM_MESSAGE ? stored.text() : "";
            if (WiredLinkDataframeCodec.utf8Length(text) > WiredLinkDataframeCodec.MAX_MESSAGE_BYTES) {
                throw new WiredSaveException(WiredLinkDataframeCodec.TOO_MUCH_TEXT);
            }
            String variableId =
                    WiredVariableOperand.normalizeToken(variableIds[i]).isEmpty() ? "" : variableIds[i];
            if (mode == WiredLinkDataframeCodec.NUMBER_VARIABLE && variableId.isEmpty()) {
                throw new WiredSaveException(WiredLinkDataframeCodec.SELECT_SOURCE_VARIABLE);
            }
            next.add(new Row(
                    key,
                    mode,
                    literal,
                    target,
                    text,
                    mode == WiredLinkDataframeCodec.NUMBER_VARIABLE || mode == WiredLinkDataframeCodec.VARIABLE_TEXT
                            ? variableId
                            : "",
                    WiredVariableOperand.normalizeFurniSource(
                            settings.furniSource(i + 1, WiredSourceUtil.SOURCE_TRIGGER)),
                    WiredVariableOperand.normalizeUserSource(settings.userSource(i, WiredSourceUtil.SOURCE_TRIGGER))));
        }

        this.saveEndpoints(settings);
        this.saveDelay(settings);
        this.rows = List.copyOf(next);
        this.sourceFurni.clear();
        Room room = settings.getRoom();
        if (room != null) {
            for (int id : settings.getFurniIds2()) {
                if (this.sourceFurni.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
                HabboItem item = room.getHabboItem(id);
                if (item != null) this.sourceFurni.add(item);
            }
        }
        return true;
    }

    private String stringParam() {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (Row row : this.rows) {
            com.google.gson.JsonArray entry = new com.google.gson.JsonArray();
            entry.add(row.key());
            entry.add(row.text());
            array.add(entry);
        }
        return array.toString();
    }

    private int[] intParams() {
        int[] params = new int[1 + 3 * ROWS];
        params[0] = this.rows.size();
        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            params[1 + i * 3] = row.mode();
            params[2 + i * 3] = row.literal();
            params[3 + i * 3] = row.target();
        }
        return params;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serialize(message, room, this.stringParam(), this.intParams());
    }

    @Override
    public String[] officialVariableIds() {
        String[] ids = new String[ROWS];
        Arrays.fill(ids, "");
        for (int i = 0; i < this.rows.size(); i++) ids[i] = this.rows.get(i).variableId();
        return ids;
    }

    @Override
    public int[] officialFurniSources() {
        int[] sources = new int[1 + ROWS];
        sources[0] = this.endpointSource;
        for (int i = 0; i < ROWS; i++) {
            sources[i + 1] = i < this.rows.size() ? this.rows.get(i).furniSource() : WiredSourceUtil.SOURCE_TRIGGER;
        }
        return sources;
    }

    @Override
    public int[] officialUserSources() {
        int[] sources = new int[ROWS];
        for (int i = 0; i < ROWS; i++) {
            sources[i] = i < this.rows.size() ? this.rows.get(i).userSource() : WiredSourceUtil.SOURCE_TRIGGER;
        }
        return sources;
    }

    @Override
    public int[] officialFurniIds2() {
        return this.sourceFurni.stream().mapToInt(HabboItem::getId).toArray();
    }

    @Override
    public String getWiredData() {
        List<Integer> secondary = new ArrayList<>();
        for (HabboItem item : this.sourceFurni) secondary.add(item.getId());
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(), this.linkIds(), this.endpointSource, new ArrayList<>(this.rows), secondary));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredUtilityPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        this.onPickUp();
        if (data == null) return;
        this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
        this.loadLinks(data.itemIds, room);
        this.endpointSource = data.endpointSource == WiredSourceUtil.SOURCE_SELECTOR
                ? WiredSourceUtil.SOURCE_SELECTOR
                : WiredSourceUtil.SOURCE_SELECTED;
        List<Row> loaded = new ArrayList<>();
        if (data.rows != null) {
            for (Row row : data.rows) {
                if (loaded.size() >= ROWS) break;
                if (row == null || row.key() == null || WiredLinkDataframeCodec.keyError(row.key()) != null) continue;
                int mode = Math.max(0, Math.min(3, row.mode()));
                loaded.add(new Row(
                        row.key(),
                        mode,
                        row.literal(),
                        WiredLinkDataframeCodec.isTarget(row.target()) ? row.target() : 1,
                        WiredLinkPayload.truncate(row.text(), WiredLinkDataframeCodec.MAX_MESSAGE_BYTES),
                        row.variableId() == null ? "" : row.variableId(),
                        WiredVariableOperand.normalizeFurniSource(row.furniSource()),
                        WiredVariableOperand.normalizeUserSource(row.userSource())));
            }
        }
        this.rows = List.copyOf(loaded);
        if (data.secondaryIds != null && room != null) {
            for (Integer id : data.secondaryIds) {
                if (id == null) continue;
                HabboItem item = room.getHabboItem(id);
                if (item != null) this.sourceFurni.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.rows = List.of();
        this.sourceFurni.clear();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int endpointSource;
        List<Row> rows;
        List<Integer> secondaryIds;

        JsonData(int delay, List<Integer> itemIds, int endpointSource, List<Row> rows, List<Integer> secondaryIds) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.endpointSource = endpointSource;
            this.rows = rows;
            this.secondaryIds = secondaryIds;
        }
    }
}
