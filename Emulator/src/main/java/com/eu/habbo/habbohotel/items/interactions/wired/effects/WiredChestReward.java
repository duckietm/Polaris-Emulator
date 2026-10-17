package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;

/**
 * The official chest reward settings both chest boxes share (give currency 45, give furni 46). Int
 * params {@code [rewarding mode (0 an amount, 1 everything), amount, amount from a variable, variable
 * target (global or context), show the reward popup by default, box extra]}; the string param is the
 * reward popup text (3 lines, 200 characters); the variable id is the definition's first variable id.
 */
final class WiredChestReward {
    static final int MODE_AMOUNT = 0;
    static final int MODE_EVERYTHING = 1;
    static final int POPUP_MAX_LENGTH = 200;
    static final int POPUP_MAX_LINES = 3;

    int mode = MODE_AMOUNT;
    int amount = 1;
    boolean amountFromVariable;
    int amountTarget = WiredValueVariable.TARGET_GLOBAL;
    int amountVariableId;
    boolean showPopupByDefault;
    String popupText = "";

    /** Reads the official save; returns the box extra (the sixth int) or {@code extraFallback}. */
    int read(WiredSettings settings, int minimumAmount, int extraFallback) {
        int[] params = settings.getIntParams();
        this.mode = (params.length > 0 && params[0] == MODE_EVERYTHING) ? MODE_EVERYTHING : MODE_AMOUNT;
        this.amount = Math.max(minimumAmount, params.length > 1 ? params[1] : minimumAmount);
        String[] variableIds = settings.getVariableIds();
        int variableId = variableIds.length > 0 ? WiredValueVariable.parseId(variableIds[0]) : 0;
        this.amountFromVariable = params.length > 2 && params[2] == 1 && variableId > 0;
        this.amountVariableId = this.amountFromVariable ? variableId : 0;
        this.amountTarget = normalizeTarget(params.length > 3 ? params[3] : WiredValueVariable.TARGET_GLOBAL);
        this.showPopupByDefault = params.length > 4 && params[4] == 1;
        this.popupText = normalizePopup(settings.getStringParam());
        return params.length > 5 ? params[5] : extraFallback;
    }

    int[] officialParams(int extra) {
        return new int[] {
            this.mode,
            this.amount,
            this.amountFromVariable ? 1 : 0,
            this.amountTarget,
            this.showPopupByDefault ? 1 : 0,
            extra
        };
    }

    String[] officialVariableIds() {
        return this.amountFromVariable ? new String[] {Integer.toString(this.amountVariableId)} : new String[0];
    }

    /** What one rewarded user gets this time: everything, the amount, or the variable's value (null: nothing). */
    Integer resolveAmount(WiredContext ctx, Room room, int minimumAmount) {
        if (this.mode == MODE_EVERYTHING) return Integer.MAX_VALUE;
        if (!this.amountFromVariable) return this.amount;
        Integer value = WiredValueVariable.read(
                ctx,
                room,
                this.amountTarget,
                this.amountVariableId,
                WiredSourceUtil.SOURCE_TRIGGER,
                WiredSourceUtil.SOURCE_TRIGGER);
        return (value == null || value < minimumAmount) ? null : value;
    }

    static int normalizeTarget(int target) {
        return target == WiredValueVariable.TARGET_CONTEXT
                ? WiredValueVariable.TARGET_CONTEXT
                : WiredValueVariable.TARGET_GLOBAL;
    }

    static String normalizePopup(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.replace("\r", "").split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length && i < POPUP_MAX_LINES; i++) {
            if (i > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.length() > POPUP_MAX_LENGTH ? out.substring(0, POPUP_MAX_LENGTH) : out.toString();
    }

    /** Back to a fresh box's settings. */
    void reset(int minimumAmount) {
        this.mode = MODE_AMOUNT;
        this.amount = minimumAmount;
        this.amountFromVariable = false;
        this.amountTarget = WiredValueVariable.TARGET_GLOBAL;
        this.amountVariableId = 0;
        this.showPopupByDefault = false;
        this.popupText = "";
    }

    void writeTo(Stored stored) {
        stored.rewardMode = this.mode;
        stored.amountFromVariable = this.amountFromVariable;
        stored.amountTarget = this.amountTarget;
        stored.amountVariableId = this.amountVariableId;
        stored.showPopupByDefault = this.showPopupByDefault;
        stored.popupText = this.popupText;
    }

    /** The official fields of a stored row; a row saved before them keeps a fresh box's values. */
    void readFrom(Stored stored) {
        this.mode = stored.rewardMode == MODE_EVERYTHING ? MODE_EVERYTHING : MODE_AMOUNT;
        this.amountVariableId = Math.max(0, stored.amountVariableId);
        this.amountFromVariable = stored.amountFromVariable && this.amountVariableId > 0;
        this.amountTarget = normalizeTarget(stored.amountTarget);
        this.showPopupByDefault = stored.showPopupByDefault;
        this.popupText = normalizePopup(stored.popupText);
    }

    /** The reward popup: the box's text, shown when the box says so and something was given. */
    /**
     * Tell the rewarded user what they got (AIR {@code WiredTransactionSuccess} type 2): the given
     * coins and furni as a rule, the box's text, and whether the reward popup opens by itself.
     */
    void announce(
            Habbo habbo,
            java.util.List<com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term>
                    given) {
        if (habbo == null || habbo.getClient() == null || given == null || given.isEmpty()) return;
        habbo.getClient()
                .sendResponse(com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer.reward(
                        given, this.popupText, this.showPopupByDefault));
    }

    /** The JSON fields the chest boxes store for these settings. */
    static class Stored {
        int rewardMode;
        boolean amountFromVariable;
        int amountTarget;
        int amountVariableId;
        boolean showPopupByDefault;
        String popupText;
    }
}
