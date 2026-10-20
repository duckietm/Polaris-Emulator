package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredDerivedVariableBox;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for the gen-3 Wired 2.0 QUEST boxes ({@code wf_var_quest} / {@code wf_var_quest_chain}). A quest
 * box is a variable-definition add-on (config-holder, like the level-up box): placed on the SAME stack as
 * a base counter variable, it exposes DERIVED read-only sub-variables (progress / is_complete / …)
 * computed from that counter against a configured {@code targetValue}. Completion is detected for free via
 * the existing {@code VARIABLE_CHANGED} event on the backing counter.
 *
 * <p>Editor layout as in AIR ({@code QuestVariable} / {@code QuestChainVariable}): no int params and the
 * string {@code variableName<TAB>questName}, the quest's code taking up to {@value #MAX_QUEST_NAME_LENGTH}
 * characters. AIR's editor has no target, so a box keeps the target it was saved with before; an editor
 * that still sends {@code [target]} sets it.</p>
 *
 * <p>Subclasses define the dialog {@link #code()} and the derived sub-variable set + formulas. All
 * sub-variables are always exposed (no selection UI in v1).</p>
 */
public abstract class WiredExtraQuestBase extends InteractionWiredExtra implements WiredDerivedVariableBox {
    static final int MAX_QUEST_NAME_LENGTH = 500;
    static final String STRING_PARAM_SPLITTER = "\t";

    protected int targetValue = 0;
    protected String variableName = "";
    private String questName = "";

    protected WiredExtraQuestBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraQuestBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Client {@code WiredActionLayoutCode} for this box's dialog (108 quest / 109 quest-chain). */
    protected abstract int code();

    /** Number of derived sub-variables this box exposes. */
    public abstract int subvariableCount();

    public int getTargetValue() {
        return this.targetValue;
    }

    /** The variable's name (AIR's VariableNameSection). */
    public String getVariableName() {
        return this.variableName;
    }

    /** The quest (resp. quest chain) code. */
    public String getQuestName() {
        return this.questName;
    }

    @Override
    public List<Integer> getSelectedSubvariables() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < this.subvariableCount(); i++) result.add(i);
        return result;
    }

    @Override
    public boolean hasSubvariable(int subType) {
        return subType >= 0 && subType < this.subvariableCount();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        if (params.length > 0) {
            this.targetValue = Math.max(0, params[0]);
        }
        String[] parts = splitStringParam(settings.getStringParam());
        this.variableName = WiredVariableNameValidator.normalizeLegacy(parts[0]);
        this.questName = normalizeText(parts[1], MAX_QUEST_NAME_LENGTH);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.targetValue, this.variableName, this.questName));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.targetValue = Math.max(0, data.targetValue);
        this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        this.questName = normalizeText(data.questName, MAX_QUEST_NAME_LENGTH);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableName + STRING_PARAM_SPLITTER + this.questName);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.code());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.targetValue = 0;
        this.variableName = "";
        this.questName = "";
    }

    /** {@code name<TAB>detail}; a string without a tab is the name alone. */
    static String[] splitStringParam(String value) {
        if (value == null) return new String[] {"", ""};

        int split = value.indexOf(STRING_PARAM_SPLITTER);
        return (split < 0)
                ? new String[] {value, ""}
                : new String[] {value.substring(0, split), value.substring(split + 1)};
    }

    static String normalizeText(String value, int maxLength) {
        if (value == null) return "";

        String normalized =
                value.replace("\t", "").replace("\r", "").replace("\n", "").trim();
        return (normalized.length() > maxLength) ? normalized.substring(0, maxLength) : normalized;
    }

    static class JsonData {
        int targetValue;
        String variableName;
        String questName;

        JsonData() {}

        JsonData(int targetValue, String variableName, String questName) {
            this.targetValue = targetValue;
            this.variableName = variableName;
            this.questName = questName;
        }
    }
}
