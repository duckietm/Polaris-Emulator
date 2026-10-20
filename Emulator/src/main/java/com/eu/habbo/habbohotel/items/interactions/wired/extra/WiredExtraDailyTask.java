package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Daily task box ({@code wf_var_daily_task}, code 2008). Placed on the tile of a user counter it
 * turns that counter into a daily one: a value last written before today began in the room's wired
 * timezone reads as 0, so each holder starts every day over. Exposes the quest sub-variables
 * {@code progress, target, is_complete, percent, remaining} against the configured target.
 *
 * <p>Editor layout as in AIR ({@code DailyTaskVariable}): no int params and the string
 * {@code variableName<TAB>taskName}, the task's code taking up to 100 characters. A box keeps the target
 * it was saved with before; an editor that still sends {@code [targetValue]} sets it.</p>
 */
public class WiredExtraDailyTask extends WiredExtraQuestBase {
    public static final int CODE = 2008;
    public static final int MAX_TASK_NAME_LENGTH = 100;

    private String taskName = "";

    public WiredExtraDailyTask(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraDailyTask(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int code() {
        return CODE;
    }

    @Override
    public int subvariableCount() {
        return WiredExtraQuest.SUBVARIABLE_COUNT;
    }

    @Override
    public String subvariableKey(int subType) {
        return switch (subType) {
            case WiredExtraQuest.SUB_PROGRESS -> "progress";
            case WiredExtraQuest.SUB_TARGET -> "target";
            case WiredExtraQuest.SUB_IS_COMPLETE -> "is_complete";
            case WiredExtraQuest.SUB_PERCENT -> "percent";
            case WiredExtraQuest.SUB_REMAINING -> "remaining";
            default -> "value";
        };
    }

    @Override
    public Integer derive(int subType, Integer baseValue) {
        if (baseValue == null) return null;

        int progress = Math.max(0, baseValue);
        int target = Math.max(0, this.targetValue);

        return switch (subType) {
            case WiredExtraQuest.SUB_PROGRESS -> progress;
            case WiredExtraQuest.SUB_TARGET -> target;
            case WiredExtraQuest.SUB_IS_COMPLETE -> (target > 0 && progress >= target) ? 1 : 0;
            case WiredExtraQuest.SUB_PERCENT ->
                (target <= 0) ? 100 : Math.max(0, Math.min(100, (int) Math.floor((progress * 100D) / target)));
            case WiredExtraQuest.SUB_REMAINING -> Math.max(0, target - progress);
            default -> null;
        };
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String previousName = this.variableName;
        super.saveData(settings, gameClient);
        String text = settings.getStringParam();
        if (text == null || !text.contains(STRING_PARAM_SPLITTER)) {
            // Our earlier editor sent the task alone.
            this.variableName = previousName;
        }
        this.taskName = normalizeTaskName(text);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.targetValue, this.taskName, this.variableName));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;

        this.targetValue = Math.max(0, data.targetValue);
        this.taskName = normalizeTaskName(data.taskName);
        this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableName + STRING_PARAM_SPLITTER + this.taskName);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.taskName = "";
    }

    public String getTaskName() {
        return this.taskName;
    }

    /** The task's code: what follows the name in {@code variableName<TAB>taskName}, or the whole string. */
    static String normalizeTaskName(String value) {
        if (value == null) return "";

        String normalized = value.substring(value.lastIndexOf('\t') + 1)
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return (normalized.length() > MAX_TASK_NAME_LENGTH)
                ? normalized.substring(0, MAX_TASK_NAME_LENGTH)
                : normalized;
    }

    static class JsonData {
        int targetValue;
        String taskName;
        String variableName;

        JsonData(int targetValue, String taskName, String variableName) {
            this.targetValue = targetValue;
            this.taskName = taskName;
            this.variableName = variableName;
        }
    }
}
