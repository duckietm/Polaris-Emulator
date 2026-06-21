package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets the figure (look) of the resolved users to a configured figure string. Reuses the
 * {@link WiredEffectType#SHOW_MESSAGE} dialog (text field = the figure + a user-source selector), so no
 * new client dialog is required. The figure is format-validated for the user's gender via
 * {@link ClothingValidationManager}; the look is applied + broadcast exactly as MimicCommand does.
 * Persisted on the user's next data save (matching the mimic path).
 */
public class WiredEffectGiveLook extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;

    private static final int MAX_LOOK_LENGTH = 256;

    private String figure = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveLook(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveLook(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.figure);
        message.appendInt(1);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
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
        String value = settings.getStringParam();
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String trimmed = value.trim();
        this.figure = trimmed.length() <= MAX_LOOK_LENGTH ? trimmed : trimmed.substring(0, MAX_LOOK_LENGTH);

        int[] params = settings.getIntParams();
        this.userSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getHabboInfo() == null) continue;

            String gender = habbo.getHabboInfo().getGender().name();
            String validated = ClothingValidationManager.validateLook(this.figure, gender);
            if (validated == null || validated.isEmpty()) continue;

            habbo.getHabboInfo().setLook(validated);
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
            }
            room.sendComposer(new RoomUserDataComposer(habbo).compose());
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.figure, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.figure = data.figure == null ? "" : data.figure;
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        } else {
            this.figure = "";
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.figure = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String figure;
        int delay;
        int userSource;

        public JsonData(String figure, int delay, int userSource) {
            this.figure = figure;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
