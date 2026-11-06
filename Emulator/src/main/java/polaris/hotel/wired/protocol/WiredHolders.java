package polaris.hotel.wired.protocol;

import java.util.List;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.extra.WiredExtraContextVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraDailyTask;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraQuest;
import polaris.hotel.items.interactions.wired.extra.WiredExtraQuestChain;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableReference;

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
