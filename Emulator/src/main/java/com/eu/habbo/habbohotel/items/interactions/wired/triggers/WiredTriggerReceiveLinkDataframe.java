package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableScope;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.link.WiredLinkDataframeCodec;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Official receive link dataframe (30, {@code wf_trg_receive_dataframe}, AIR {@code
 * LinkDataframeReceivePreset}): fires when one of the picked WIRED Links receives a dataframe. Up to 12
 * rows each name a key and where its value goes: a text placeholder of the stack ({@code $(name)}), or a
 * writable user-made context variable when the value is a whole number.
 *
 * <p>Int params {@code [rowCount, 12 x kind]} (0 placeholder, 1 variable), string param {@code [[key,
 * placeholder], ...]}, 12 variable ids (the context variable of each variable row).
 */
public class WiredTriggerReceiveLinkDataframe extends WiredTriggerReceiveLinkBase implements WiredOfficialVariableIds {
    public static final WiredTriggerType type = WiredTriggerType.RECEIVE_LINK_DATAFRAME;

    private List<RowConfig> rows = List.of();

    /** One row: the normalized key, its kind, the placeholder name and the context variable. */
    record RowConfig(String key, int kind, String placeholder, int variableId) {}

    public WiredTriggerReceiveLinkDataframe(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerReceiveLinkDataframe(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredLinkPayload.Kind linkKind() {
        return WiredLinkPayload.Kind.DATAFRAME;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event.getType() == WiredEvent.Type.LINK_DATAFRAME_RECEIVED && this.matchesLink(event);
    }

    /**
     * The event of this trigger's stack: placeholder rows become the event's link values (placeholder
     * name to text), variable rows are written into a fresh context-variable scope.
     */
    @Override
    public WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops) {
        return this.receiveEvent(room, link, payload, hops, 0);
    }

    @Override
    public WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops, int sourceRoomId) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        WiredContextVariableScope scope = new WiredContextVariableScope();
        for (RowConfig row : this.rows) {
            String value = payload.values().get(row.key());
            if (value == null) {
                // A placeholder whose key the dataframe lacks reads "null" (observed on AIR).
                if (row.kind() == WiredLinkDataframeCodec.PLACEHOLDER_DESTINATION)
                    placeholders.put(row.placeholder(), "null");
                continue;
            }
            if (row.kind() == WiredLinkDataframeCodec.PLACEHOLDER_DESTINATION) {
                placeholders.put(row.placeholder(), value);
            } else if (WiredLinkDataframeCodec.isLiteral(value) && isCaptureVariable(room, row.variableId())) {
                scope.assignValue(row.variableId(), Integer.parseInt(value), true);
            }
        }
        return WiredEvent.builder(WiredEvent.Type.LINK_DATAFRAME_RECEIVED, room)
                .sourceItem(link)
                .tile(room.getLayout() != null ? room.getLayout().getTile(link.getX(), link.getY()) : null)
                .linkValues(placeholders)
                .contextVariableScope(scope)
                .linkHops(hops)
                .linkSourceRoomId(sourceRoomId)
                .triggeredByEffect(true)
                .build();
    }

    /** A writable, user-made context variable holding a number (AIR LinkDataframeReceiveRowPreset.variableFilter). */
    static boolean isCaptureVariable(Room room, int variableId) {
        WiredExtraContextVariable definition = WiredContextVariableSupport.getDefinition(room, variableId);
        return definition != null && definition.hasValue() && !definition.isArray();
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] intParams = settings.getIntParams();
        String[] variableIds = WiredLinkDataframeCodec.twelveVariableIds(settings.getVariableIds());
        if (intParams.length != 1 + WiredLinkDataframeCodec.MAX_ROWS) {
            throw new WiredTriggerSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
        }
        int count = intParams[0];
        List<WiredLinkDataframeCodec.Row> decoded = WiredLinkDataframeCodec.decode(settings.getStringParam(), count);
        if (decoded == null) {
            throw new WiredTriggerSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
        }
        String sizeError = WiredLinkDataframeCodec.storageError(settings.getStringParam(), intParams, variableIds);
        if (sizeError != null) {
            throw new WiredTriggerSaveException(sizeError);
        }

        Room room = settings.getRoom();
        Set<String> keys = new HashSet<>();
        Set<String> destinations = new HashSet<>();
        List<RowConfig> next = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            WiredLinkDataframeCodec.Row row = decoded.get(i);
            int kind = intParams[i + 1];
            String keyError = WiredLinkDataframeCodec.keyError(row.key());
            if (keyError != null) throw new WiredTriggerSaveException(keyError);
            String key = WiredLinkDataframeCodec.normalizeKey(row.key());
            if (!keys.add(key)) throw new WiredTriggerSaveException(WiredLinkDataframeCodec.NAME_UNIQUE);

            String placeholder = "";
            int variableId = 0;
            if (kind == WiredLinkDataframeCodec.PLACEHOLDER_DESTINATION) {
                placeholder = row.text().toLowerCase(java.util.Locale.ROOT);
                String error = WiredLinkDataframeCodec.placeholderError(placeholder);
                if (error != null) throw new WiredTriggerSaveException(error);
                if (!destinations.add("text:" + placeholder)) {
                    throw new WiredTriggerSaveException(WiredLinkDataframeCodec.DESTINATION_CONFLICT);
                }
            } else if (kind == WiredLinkDataframeCodec.VARIABLE_DESTINATION) {
                variableId = WiredLinkDataframeCodec.parseVariableId(variableIds[i]);
                if (room != null && !isCaptureVariable(room, variableId)) {
                    throw new WiredTriggerSaveException(WiredLinkDataframeCodec.INVALID_CAPTURE_VARIABLE);
                }
                if (!destinations.add("variable:" + variableId)) {
                    throw new WiredTriggerSaveException(WiredLinkDataframeCodec.DESTINATION_CONFLICT);
                }
            } else {
                throw new WiredTriggerSaveException(WiredLinkDataframeCodec.INVALID_DATAFRAME);
            }
            next.add(new RowConfig(key, kind, placeholder, variableId));
        }

        this.savePicks(settings);
        this.rows = List.copyOf(next);
        return true;
    }

    /** The placeholder names this trigger fills, for the message boxes' editor context. */
    public List<String> placeholderNames() {
        List<String> names = new ArrayList<>();
        for (RowConfig row : this.rows) {
            if (row.kind() == WiredLinkDataframeCodec.PLACEHOLDER_DESTINATION) names.add(row.placeholder());
        }
        return names;
    }

    @Override
    public String[] officialVariableIds() {
        String[] ids = new String[WiredLinkDataframeCodec.MAX_ROWS];
        for (int i = 0; i < ids.length; i++) {
            RowConfig row = i < this.rows.size() ? this.rows.get(i) : null;
            ids[i] = row != null && row.variableId() > 0 ? Integer.toString(row.variableId()) : "";
        }
        return ids;
    }

    private String stringParam() {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (RowConfig row : this.rows) {
            com.google.gson.JsonArray entry = new com.google.gson.JsonArray();
            entry.add(row.key());
            entry.add(row.placeholder());
            array.add(entry);
        }
        return array.toString();
    }

    private int[] intParams() {
        int[] params = new int[1 + WiredLinkDataframeCodec.MAX_ROWS];
        params[0] = this.rows.size();
        for (int i = 0; i < this.rows.size(); i++)
            params[i + 1] = this.rows.get(i).kind();
        return params;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serialize(message, room, this.stringParam(), this.intParams());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.linkIds(), new ArrayList<>(this.rows)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        JsonData data = null;
        if (wiredData != null && wiredData.startsWith("{")) {
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException e) {
                data = null;
            }
        }
        this.loadLinks(data != null ? data.itemIds : null, room);
        List<RowConfig> loaded = new ArrayList<>();
        if (data != null && data.rows != null) {
            for (RowConfig row : data.rows) {
                if (loaded.size() >= WiredLinkDataframeCodec.MAX_ROWS) break;
                if (row == null || row.key() == null || WiredLinkDataframeCodec.keyError(row.key()) != null) continue;
                String placeholder = row.placeholder() == null ? "" : row.placeholder();
                loaded.add(
                        new RowConfig(row.key(), row.kind() == 1 ? 1 : 0, placeholder, Math.max(0, row.variableId())));
            }
        }
        this.rows = List.copyOf(loaded);
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.rows = List.of();
    }

    static class JsonData {
        List<Integer> itemIds;
        List<RowConfig> rows;

        JsonData(List<Integer> itemIds, List<RowConfig> rows) {
            this.itemIds = itemIds;
            this.rows = rows;
        }
    }
}
