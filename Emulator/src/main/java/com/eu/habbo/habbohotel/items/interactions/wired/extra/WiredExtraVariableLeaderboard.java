package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredExecutionOrderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.leaderboard.WiredLeaderboardCodec;
import com.eu.habbo.habbohotel.wired.leaderboard.WiredLeaderboardConfig;
import com.eu.habbo.habbohotel.wired.leaderboard.WiredLeaderboardSupport;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Variable leaderboard addon (wf_xtra_var_leaderboard, official addon code 1005): a ranking panel of
 * the user variables it is stacked with, drawn in the room over the tile its config names. The box
 * only stores the official config ({@link WiredLeaderboardCodec}); the room's
 * {@link com.eu.habbo.habbohotel.wired.leaderboard.WiredLeaderboardService} sends the panels and
 * their rows to the players.
 *
 * <p>The editor packet carries the official int params and, in the string param, the 17 text fields
 * followed by the 12 variable ids. After the usual addon fields comes the official editor context of
 * type 8: the published overlay id, this tile and the variables a leaderboard here can rank.
 */
public class WiredExtraVariableLeaderboard extends InteractionWiredExtra implements WiredOfficialVariableIds {
    public static final int CODE = 1005;
    public static final int CONTEXT_TYPE = 8;

    private static final int MAX_CONTEXT_CHOICES = 50;

    /** The stored official config; null for a box nobody configured yet. */
    private WiredLeaderboardCodec.Stored stored;

    public WiredExtraVariableLeaderboard(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableLeaderboard(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Whether the box has been saved with a config, and so is shown in the room. */
    public boolean isPublished() {
        return this.stored != null;
    }

    /** The box's config; a box nobody configured reads as the defaults at its own tile. */
    public WiredLeaderboardConfig getConfig() {
        if (this.stored != null) {
            WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(this.stored, this.getX(), this.getY());
            if (decoded.valid()) {
                return decoded.config();
            }
        }
        return WiredLeaderboardCodec.defaults(this.getX(), this.getY());
    }

    /**
     * The user variables a leaderboard on this tile can rank, in execution order: the plain (not
     * array) user variable boxes it is stacked with.
     */
    public List<WiredExtraUserVariable> getCandidates(Room room) {
        List<WiredExtraUserVariable> candidates = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null) {
            return candidates;
        }
        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(this.getX(), this.getY());
        if (extras == null || extras.isEmpty()) {
            return candidates;
        }
        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraUserVariable variable && !variable.isArray()) {
                candidates.add(variable);
            }
        }
        return candidates;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        WiredLeaderboardCodec.Stored shown = this.stored != null
                ? this.stored
                : WiredLeaderboardCodec.encode(WiredLeaderboardCodec.defaults(this.getX(), this.getY()));
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        // The official string fields; their variable ids go out as the definition's variable ids.
        message.appendString(this.stored != null ? this.stored.stringParam() : "");
        message.appendInt(shown.intParams().length);
        for (int param : shown.intParams()) {
            message.appendInt(param);
        }
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);

        List<WiredExtraUserVariable> candidates = this.getCandidates(room);
        int shownChoices = Math.min(candidates.size(), MAX_CONTEXT_CHOICES);
        message.appendInt(1);
        message.appendInt(CONTEXT_TYPE);
        message.appendInt(WiredLeaderboardSupport.overlayIdOf(room, this));
        message.appendInt(this.getX());
        message.appendInt(this.getY());
        message.appendInt(candidates.size());
        message.appendInt(shownChoices);
        for (int i = 0; i < shownChoices; i++) {
            WiredExtraUserVariable variable = candidates.get(i);
            message.appendString(Integer.toString(variable.getId()));
            message.appendString(variable.getVariableName());
            message.appendBoolean(variable.isPermanentAvailability());
            message.appendBoolean(variable.hasValue());
            message.appendInt(WiredVariableLevelSystemSupport.getLevelSystem(room, variable) != null ? 1 : 0);
        }
    }

    @Override
    public String[] officialVariableIds() {
        return this.stored != null && this.stored.variableIds() != null ? this.stored.variableIds() : new String[0];
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        WiredLeaderboardCodec.Stored wire = WiredLeaderboardCodec.fromSave(
                settings.getIntParams(), settings.getStringParam(), settings.getVariableIds());
        if (wire == null || wire.stringParam().isEmpty()) {
            throw new WiredSaveException(errorKey("invalid_configuration"));
        }
        WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(wire, this.getX(), this.getY());
        if (!decoded.valid()) {
            throw new WiredSaveException(errorKey(decoded.error()));
        }
        // AIR's leaderboard picker leaves out pointers: a leaderboard is read without a running stack.
        Room savedRoom = settings.getRoom();
        if (savedRoom != null) {
            for (String variableId : wire.variableIds()) {
                if (WiredPointerVariableSupport.isPointer(savedRoom, trailingId(variableId))) {
                    throw new WiredSaveException(WiredPointerVariableSupport.REQUIRES_EXECUTION_CONTEXT_ERROR);
                }
            }
        }
        this.stored = WiredLeaderboardCodec.encode(decoded.config());

        Room room = (gameClient != null && gameClient.getHabbo() != null)
                ? gameClient.getHabbo().getHabboInfo().getCurrentRoom()
                : null;
        if (room != null) {
            WiredLeaderboardSupport.ensure(room);
        }
        return true;
    }

    /** The item id at the end of a variable id ({@code 12}, {@code custom:12}, {@code user:12}), or 0. */
    static int trailingId(String variableId) {
        if (variableId == null) return 0;
        String text = variableId.substring(variableId.lastIndexOf(':') + 1).trim();
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getWiredData() {
        if (this.stored == null) {
            return "";
        }
        return WiredManager.getGson()
                .toJson(new JsonData(this.stored.intParams(), this.stored.variableIds(), this.stored.stringParam()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null || data.intParams == null || data.variableIds == null || data.stringParam == null) {
            return;
        }
        WiredLeaderboardCodec.Stored loaded =
                new WiredLeaderboardCodec.Stored(data.intParams, data.variableIds, data.stringParam);
        WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(loaded, this.getX(), this.getY());
        if (decoded.valid() && !data.stringParam.isEmpty()) {
            this.stored = WiredLeaderboardCodec.encode(decoded.config());
        }

        if (room != null) {
            WiredLeaderboardSupport.ensure(room);
        }
    }

    @Override
    public void onPickUp() {
        this.stored = null;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    private static String errorKey(String error) {
        return "wiredfurni.error.variable_leaderboard." + (error != null ? error : "invalid_configuration");
    }

    static class JsonData {
        int[] intParams;
        String[] variableIds;
        String stringParam;

        JsonData(int[] intParams, String[] variableIds, String stringParam) {
            this.intParams = intParams;
            this.variableIds = variableIds;
            this.stringParam = stringParam;
        }
    }
}
