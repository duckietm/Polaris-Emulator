package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Exposes one room variable to an outside caller over HTTP.
 *
 * <p>The keys are minted here and never read from the save, because a key the client chooses is not
 * a credential: a room owner could set a value they already know from somewhere else, or two rooms
 * could end up sharing one. The client is told what the keys are; it does not get to pick them.
 * Asking for a fresh pair is the only say the client has over them.
 *
 * <p>Reading is on as soon as a variable is bound. Writing stays off until the room owner turns it
 * on, so a key that leaks can at worst be used to watch a counter rather than to drive the room.
 */
public class WiredExtraVariableWebApi extends InteractionWiredExtra {
    public static final int CODE = 128;
    public static final int KEY_BYTES = 24;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final char FIELD_SEPARATOR = '\t';

    private String variableToken = "";
    private int variableItemId = 0;
    private String readKey = "";
    private String writeKey = "";
    private boolean writeEnabled = false;

    public WiredExtraVariableWebApi(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableWebApi(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static String mintKey() {
        byte[] material = new byte[KEY_BYTES];
        RANDOM.nextBytes(material);
        return KEY_ENCODER.encodeToString(material);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        int[] intParams = settings.getIntParams();
        String nextVariableToken = normalizeVariableToken(firstField(settings.getStringParam()));
        int nextVariableItemId = getCustomItemId(nextVariableToken);

        if (nextVariableItemId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        WiredVariableDefinitionInfo definitionInfo =
                WiredContextVariableSupport.getDefinitionInfo(room, nextVariableItemId);
        if (definitionInfo == null || !definitionInfo.hasValue()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        this.variableToken = nextVariableToken;
        this.variableItemId = nextVariableItemId;
        this.writeEnabled = intParams.length > 0 && intParams[0] == 1;

        // A pair that does not exist yet is minted, and the room owner can ask for a fresh one. Both
        // keys turn over together: keeping the read key alive across a rotation would leave a leaked
        // pair half usable, which is the state a rotation exists to end.
        boolean rotate = intParams.length > 1 && intParams[1] == 1;
        if (rotate || this.readKey.isEmpty() || this.writeKey.isEmpty()) {
            this.readKey = mintKey();
            this.writeKey = mintKey();
        }

        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.variableToken, this.variableItemId, this.readKey, this.writeKey, this.writeEnabled));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken + FIELD_SEPARATOR + this.readKey + FIELD_SEPARATOR + this.writeKey);
        message.appendInt(1);
        message.appendInt(this.writeEnabled ? 1 : 0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setExtradata("");

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.variableToken = normalizeVariableToken(data.variableToken);
                this.variableItemId =
                        data.variableItemId > 0 ? data.variableItemId : getCustomItemId(this.variableToken);
                this.readKey = data.readKey == null ? "" : data.readKey;
                this.writeKey = data.writeKey == null ? "" : data.writeKey;
                this.writeEnabled = data.writeEnabled;
            }
            return;
        }

        this.variableToken = normalizeVariableToken(firstField(wiredData));
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    @Override
    public void onPickUp() {
        // Picking the box up ends the exposure: the keys go with it, so a caller holding the old
        // pair cannot reach the variable again if the box is put back down.
        this.variableToken = "";
        this.variableItemId = 0;
        this.readKey = "";
        this.writeKey = "";
        this.writeEnabled = false;
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getVariableToken() {
        return this.variableToken;
    }

    public int getVariableItemId() {
        return this.variableItemId;
    }

    public String getReadKey() {
        return this.readKey;
    }

    public String getWriteKey() {
        return this.writeKey;
    }

    public boolean isWriteEnabled() {
        return this.writeEnabled;
    }

    static String firstField(String stringParam) {
        if (stringParam == null) {
            return "";
        }
        int separator = stringParam.indexOf(FIELD_SEPARATOR);
        return separator < 0 ? stringParam : stringParam.substring(0, separator);
    }

    static String normalizeVariableToken(String token) {
        return token == null ? "" : token.trim();
    }

    static int getCustomItemId(String token) {
        String digits = token == null ? "" : token.trim();
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static class JsonData {
        String variableToken;
        int variableItemId;
        String readKey;
        String writeKey;
        boolean writeEnabled;

        JsonData(String variableToken, int variableItemId, String readKey, String writeKey, boolean writeEnabled) {
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.readKey = readKey;
            this.writeKey = writeKey;
            this.writeEnabled = writeEnabled;
        }
    }
}
