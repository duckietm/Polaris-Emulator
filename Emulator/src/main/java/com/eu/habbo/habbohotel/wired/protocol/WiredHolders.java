package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraDailyTask;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuestChain;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import java.util.List;

/**
 * Which official definition packet a wired box belongs to. The official client has six: triggers,
 * actions, conditions, addons, selectors and variables. Our selectors are effects and our addons and
 * variable definitions are extras; the variable definitions are the official variable types (furni,
 * user, global, context, reference, quest, quest chain, echo, daily task, pointer).
 */
public final class WiredHolders {
    private static final List<Class<? extends InteractionWiredExtra>> VARIABLES = List.of(
            WiredExtraFurniVariable.class,
            WiredExtraUserVariable.class,
            WiredExtraRoomVariable.class,
            WiredExtraContextVariable.class,
            WiredExtraVariableReference.class,
            WiredExtraQuest.class,
            WiredExtraQuestChain.class,
            WiredExtraVariableEcho.class,
            WiredExtraDailyTask.class,
            WiredExtraPointerVariable.class);

    private WiredHolders() {}

    public static WiredDefinitionWire.Holder of(InteractionWired box) {
        if (box instanceof InteractionWiredTrigger) return WiredDefinitionWire.Holder.TRIGGER;
        if (box instanceof InteractionWiredCondition) return WiredDefinitionWire.Holder.CONDITION;
        if (box instanceof InteractionWiredEffect effect) {
            return effect.isSelector() ? WiredDefinitionWire.Holder.SELECTOR : WiredDefinitionWire.Holder.ACTION;
        }
        if (box instanceof InteractionWiredExtra extra) {
            return isVariable(extra) ? WiredDefinitionWire.Holder.VARIABLE : WiredDefinitionWire.Holder.ADDON;
        }
        return WiredDefinitionWire.Holder.ACTION;
    }

    public static boolean isVariable(InteractionWiredExtra extra) {
        for (Class<? extends InteractionWiredExtra> type : VARIABLES) {
            if (type.isInstance(extra)) return true;
        }
        return false;
    }
}
