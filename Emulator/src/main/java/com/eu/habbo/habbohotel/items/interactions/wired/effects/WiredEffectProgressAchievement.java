package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredRewardPolicy;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAchievementEnabler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgress;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgressPolicy;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredProgressLimiter;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Habbo's progress-achievement action: progresses one of the room's achievements for the selected
 * users, by a fixed amount ({@code mode 1}) or up to it ({@code mode 0}).
 *
 * <p>Achievements are hotel-wide, so this only works when the hotel turns it on
 * ({@code hotel.wired.achievements.enabled}), for achievements on its allow-list that an achievement
 * enabler in the same room also names, for users still in the room, and within each user's
 * allowance per window. Saving it needs the value-granting wired permission.
 *
 * <p>Official layout (AIR code 51). String param: the achievement name; variable ids {@code [the variable the amount
 * may come from]} (older clients sent its id after a tab in the string; still read). Int params {@code [mode, valueIsVariable, value, valueTarget]}:
 * the amount is the number, or that variable read once on the first holder of its target (0 furni,
 * read on the furni slot; 1 user, read on the second user slot; -10 global; -20 context). User slot 0
 * is who is progressed.
 */
public class WiredEffectProgressAchievement extends InteractionWiredEffect
        implements WiredOfficialSources, WiredOfficialVariableIds {
    public static final WiredEffectType type = WiredEffectType.PROGRESS_ACHIEVEMENT;
    public static final int MAX_NAME_LENGTH = WiredExtraAchievementEnabler.MAX_NAME_LENGTH;

    private String achievement = "";
    private int mode = WiredHotelProgress.MODE_ADD;
    private int amount = 1;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean valueIsVariable = false;
    private int valueVariableId = 0;
    private int valueTarget = WiredValueVariable.TARGET_FURNI;
    private int valueFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int valueUserSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectProgressAchievement(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectProgressAchievement(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.achievement);
        message.appendInt(4);
        message.appendInt(this.mode);
        message.appendInt(this.valueIsVariable ? 1 : 0);
        message.appendInt(this.amount);
        message.appendInt(this.valueTarget);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        WiredEffectProgressRewardTrack.appendInvalidTriggers(this, message, room);
    }

    @Override
    public String[] officialVariableIds() {
        return WiredVariableIds.list(
                this.valueIsVariable ? WiredVariableIds.toWire(this.valueTarget, this.valueVariableId) : "");
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        String text = settings.getStringParam() == null ? "" : settings.getStringParam();
        int tab = text.indexOf('\t');
        String name = normalizeName(tab < 0 ? text : text.substring(0, tab));
        if (name.isEmpty()) {
            return false;
        }

        int[] params = settings.getIntParams();
        boolean isVariable = params.length > 1 && params[1] == 1;
        // AIR: the amount's variable is the first variable id; older clients sent it after a tab in the string.
        int variableId = WiredVariableIds.present(settings.getVariableIds())
                ? WiredVariableIds.boxIdOf(settings.getVariableIds()[0])
                : (tab < 0 ? 0 : WiredValueVariable.parseId(text.substring(tab + 1)));
        if (isVariable && variableId <= 0) {
            return false;
        }

        int[] users = settings.getUserSourceTypes();
        int[] furni = settings.getFurniSourceTypes();
        this.achievement = name;
        this.mode = WiredHotelProgress.normalizeMode(params.length > 0 ? params[0] : WiredHotelProgress.MODE_ADD);
        this.valueIsVariable = isVariable;
        this.amount = WiredHotelProgress.normalizeAchievementAmount(params.length > 2 ? params[2] : 1);
        this.valueTarget = WiredValueVariable.normalizeTarget(params.length > 3 ? params[3] : 0);
        this.valueVariableId = variableId;
        this.userSource =
                WiredVariableOperand.normalizeUserSource(users.length > 0 ? users[0] : WiredSourceUtil.SOURCE_TRIGGER);
        this.valueUserSource =
                WiredVariableOperand.normalizeUserSource(users.length > 1 ? users[1] : WiredSourceUtil.SOURCE_TRIGGER);
        this.valueFurniSource = furni.length > 0 ? furni[0] : WiredSourceUtil.SOURCE_TRIGGER;
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
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        WiredEngine engine = WiredManager.getEngine();
        WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.achievements(WiredPlatform.configuration());
        if (room == null || environment == null || engine == null || !policy.allows(this.achievement)) {
            return;
        }

        Set<String> declared = WiredExtraAchievementEnabler.declaredAchievements(room);
        if (!declared.contains(this.achievement)) {
            return;
        }

        AchievementManager manager = environment.getAchievementManager();
        Achievement target = manager == null ? null : manager.getAchievement(this.achievement);
        List<Habbo> users = WiredHotelProgress.presentUsers(room, WiredSourceUtil.resolveUsers(ctx, this.userSource));
        if (target == null || users.isEmpty()) {
            return;
        }

        int mode = this.mode;
        int value = this.amount;
        if (this.valueIsVariable) {
            Integer read = WiredValueVariable.read(
                    ctx, room, this.valueTarget, this.valueVariableId, this.valueFurniSource, this.valueUserSource);
            if (read == null || read <= 0) {
                return;
            }
            value = read;
        }
        int amount = value;
        WiredProgressLimiter limiter = engine.progressLimiter();
        WiredHotelProgress.dispatch(() -> WiredHotelProgress.progressAchievement(
                room, users, target, declared, mode, amount, policy, limiter, AchievementManager::progressAchievement));
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException e) {
            return;
        }
        if (data == null) {
            return;
        }

        this.achievement = normalizeName(data.achievement);
        this.mode = WiredHotelProgress.normalizeMode(data.mode);
        this.amount = data.amount == null ? 1 : WiredHotelProgress.normalizeAchievementAmount(data.amount);
        this.userSource = WiredVariableOperand.normalizeUserSource(data.userSource);
        this.valueIsVariable = data.valueIsVariable && data.valueVariableId > 0;
        this.valueVariableId = Math.max(0, data.valueVariableId);
        this.valueTarget = WiredValueVariable.normalizeTarget(data.valueTarget);
        this.valueFurniSource = data.valueFurniSource;
        this.valueUserSource = WiredVariableOperand.normalizeUserSource(data.valueUserSource);
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.achievement = "";
        this.mode = WiredHotelProgress.MODE_ADD;
        this.amount = 1;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.valueIsVariable = false;
        this.valueVariableId = 0;
        this.valueTarget = WiredValueVariable.TARGET_FURNI;
        this.valueFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.valueUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.valueFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.valueUserSource};
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER
                || (this.valueIsVariable
                        && this.valueTarget == WiredValueVariable.TARGET_USER
                        && this.valueUserSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    public String getAchievement() {
        return this.achievement;
    }

    public int getMode() {
        return this.mode;
    }

    public int getAmount() {
        return this.amount;
    }

    public int getUserSource() {
        return this.userSource;
    }

    /** One name as the enabler accepts it, or empty. */
    static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        List<String> names = WiredExtraAchievementEnabler.parseNames(value.trim());
        return names.size() == 1 && names.get(0).length() <= MAX_NAME_LENGTH ? names.get(0) : "";
    }

    static class JsonData {
        String achievement;
        int mode;
        /** Boxed so a row without it (older JSON) keeps the old default of 1; a stored 0 stays 0. */
        Integer amount;

        int delay;
        int userSource;
        boolean valueIsVariable;
        int valueVariableId;
        int valueTarget;
        int valueFurniSource;
        int valueUserSource;

        JsonData(WiredEffectProgressAchievement box) {
            this.achievement = box.achievement;
            this.mode = box.mode;
            this.amount = box.amount;
            this.delay = box.getDelay();
            this.userSource = box.userSource;
            this.valueIsVariable = box.valueIsVariable;
            this.valueVariableId = box.valueVariableId;
            this.valueTarget = box.valueTarget;
            this.valueFurniSource = box.valueFurniSource;
            this.valueUserSource = box.valueUserSource;
        }
    }
}
