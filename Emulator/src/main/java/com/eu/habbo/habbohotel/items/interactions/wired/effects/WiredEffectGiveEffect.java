package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Official give effect (52, {@code wf_act_give_effect}). Int params {@code [effect id (0..10000, 13 for
 * a new box), priority (0..2, 2 for a new box), type (0 or 1)]}; the user source is the official slot.
 * The effect stays until the user leaves the room or something else changes it.
 *
 * <p>AIR only names the two fields, and the texts are not in the official set, so their meaning is
 * ours. Priority: a box does nothing to a user who wears an effect a box with a higher priority gave
 * them; equal or higher replaces it. An effect the user got elsewhere (their own, a tile, a game)
 * has no wired priority. Type 0 gives the effect whatever the user wears; type 1 leaves a user alone
 * who wears an effect that did not come from a give effect box, so their own effect wins. Effect id
 * 0 takes the effect off under the same rules.
 *
 * <p>Before the official layout the box borrowed the show-message editor and kept the effect id as
 * text; those rows still load.
 */
// Still a WiredEffectWhisper for plugin binary compatibility; it overrides everything it uses.
public class WiredEffectGiveEffect extends WiredEffectWhisper {
    public static final int MAX_EFFECT_ID = 10_000;
    static final int DEFAULT_EFFECT_ID = 13;
    static final int DEFAULT_PRIORITY = 2;
    static final int TYPE_REPLACE = 0;
    static final int TYPE_KEEP_OWN = 1;

    private int effectId = DEFAULT_EFFECT_ID;
    private int priority = DEFAULT_PRIORITY;
    private int effectType = TYPE_REPLACE;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || this.effectId < 0) return;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            if (!this.appliesTo(roomUnit)) continue;
            // An effect reserved for a higher rank (staff, ambassador) is not put on a user below it.
            if (blockedFor(this.effectId, room.getHabbo(roomUnit))) continue;

            room.giveEffect(roomUnit, this.effectId, Integer.MAX_VALUE);
            // Effects off in the room, or a rider: the effect did not change, so no priority is kept.
            if (roomUnit.getEffectId() == this.effectId) {
                roomUnit.setWiredEffect(this.effectId, this.priority);
            }
        }
    }

    /**
     * Whether the effect is reserved for a rank above this user's, as :enable and the effect packet
     * already check: the box used to let a room owner dress themselves or visitors as staff.
     */
    static boolean blockedFor(int effectId, Habbo habbo) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();

        return environment != null
                && habbo != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getRank() != null
                && environment
                        .getPermissionsManager()
                        .isEffectBlocked(
                                effectId, habbo.getHabboInfo().getRank().getId());
    }

    boolean appliesTo(RoomUnit roomUnit) {
        if (roomUnit == null) return false;

        int activePriority = roomUnit.getWiredEffectPriority();
        if (activePriority > this.priority) return false;

        return this.effectType != TYPE_KEEP_OWN || activePriority >= 0 || roomUnit.getEffectId() == 0;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 1) throw new WiredSaveException("Invalid effect");

        int effectId = clamp(params[0], 0, MAX_EFFECT_ID);
        if (gameClient != null && blockedFor(effectId, gameClient.getHabbo())) {
            throw new WiredSaveException("This effect is reserved for a higher rank");
        }

        this.effectId = effectId;
        this.priority = clamp(params.length > 1 ? params[1] : DEFAULT_PRIORITY, 0, 2);
        this.effectType = (params.length > 2 && params[2] == TYPE_KEEP_OWN) ? TYPE_KEEP_OWN : TYPE_REPLACE;
        this.userSource = WiredUtilityPayloadGuard.userSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData();
        data.effectId = this.effectId;
        data.priority = this.priority;
        data.effectType = this.effectType;
        data.userSource = this.userSource;
        data.delay = this.getDelay();
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            // The tab row of the oldest boxes: delay, then the effect id.
            String[] split = wiredData != null ? wiredData.split("\t") : new String[0];
            if (split.length >= 2) {
                this.setDelay(WiredUtilityPayloadGuard.parseDelay(split[0]));
                this.effectId = parseEffect(split[1]);
            }
            return;
        }

        this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
        this.userSource = data.userSource != null
                ? WiredUtilityPayloadGuard.userSource(data.userSource)
                : WiredSourceUtil.SOURCE_TRIGGER;
        // A row from the show-message editor keeps its effect id as text.
        this.effectId = data.effectId != null ? clamp(data.effectId, 0, MAX_EFFECT_ID) : parseEffect(data.message);
        this.priority = data.priority != null ? clamp(data.priority, 0, 2) : DEFAULT_PRIORITY;
        this.effectType = (data.effectType != null && data.effectType == TYPE_KEEP_OWN) ? TYPE_KEEP_OWN : TYPE_REPLACE;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.effectId);
        message.appendInt(this.priority);
        message.appendInt(this.effectType);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!trigger.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(trigger.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer id : invalidTriggers) message.appendInt(id);
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public void onPickUp() {
        this.effectId = DEFAULT_EFFECT_ID;
        this.priority = DEFAULT_PRIORITY;
        this.effectType = TYPE_REPLACE;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.EFFECT_ID;
    }

    int getEffectId() {
        return this.effectId;
    }

    private static int parseEffect(String text) {
        try {
            return clamp(Integer.parseInt(text == null ? "" : text.trim()), 0, MAX_EFFECT_ID);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static class JsonData {
        Integer effectId;
        Integer priority;
        Integer effectType;
        Integer userSource;
        int delay;
        // The effect id as the show-message editor stored it.
        String message;
    }
}
