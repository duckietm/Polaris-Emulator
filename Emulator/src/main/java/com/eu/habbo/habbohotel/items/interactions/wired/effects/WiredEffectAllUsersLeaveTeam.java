package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
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
import java.util.Collection;
import java.util.List;

public class WiredEffectAllUsersLeaveTeam extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.ALL_USERS_LEAVE_TEAM;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectAllUsersLeaveTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAllUsersLeaveTeam(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        Room room = ctx.room();
        List<Habbo> snapshot = new ArrayList<>();

        // The box is "all users leave team": on its default source it empties every team in the
        // room, whoever fired it. Any other source narrows it to the users that source resolves,
        // which the saved userSource used to promise and execute ignored.
        if (this.userSource == WiredSourceUtil.SOURCE_TRIGGER) {
            Collection<Habbo> currentHabbos =
                    room.getCurrentHabbos() != null ? room.getCurrentHabbos().values() : null;
            if (currentHabbos == null || currentHabbos.isEmpty()) {
                return;
            }
            Habbo[] copy = currentHabbos.toArray(Habbo[]::new);
            if (copy == null) {
                return;
            }
            for (Habbo h : copy) snapshot.add(h);
        } else {
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                Habbo habbo = room.getHabbo(unit);
                if (habbo != null) snapshot.add(habbo);
            }
        }

        for (Habbo h : snapshot) {
            if (h == null || h.getHabboInfo() == null) continue;
            Class<? extends com.eu.habbo.habbohotel.games.Game> g =
                    h.getHabboInfo().getCurrentGame();
            if (g == null) continue;
            com.eu.habbo.habbohotel.games.Game game = ctx.room().getGame(g);
            if (game != null) game.removeHabbo(h);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
            this.userSource = data.userSource;
        } else {
            // A legacy row is the bare delay; anything else keeps the furni loading with no delay.
            this.setDelay(WiredUtilityPayloadGuard.parseDelay(wiredData));
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // Official: no int params; the user source is the official source slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.setDelay(delay);
        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int delay;
        int userSource;

        public JsonData(int delay, int userSource) {
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
