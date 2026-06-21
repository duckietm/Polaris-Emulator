package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.*;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboLeavesRoom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Phase A — verifies the DB-furnidata name aliases added to {@link ItemManager#loadItemInteractions()}
 * resolve to the intended existing wired class. This is the framework-level guarantee (the live
 * client-dialog/save round-trip still needs an in-room smoke test). It also guards against an alias
 * line being accidentally removed or repointed in the future.
 */
class WiredAliasResolutionTest {

    private static ItemManager itemManager;

    @BeforeAll
    static void setUp() {
        itemManager = new ItemManager();
        itemManager.loadItemInteractions();
    }

    @Test
    void aliasesResolveToTheirCanonicalWiredClass() {
        Map<String, Class<?>> expected = new LinkedHashMap<>();
        // effects
        expected.put("wf_act_alert_habbo", WiredEffectAlert.class);
        expected.put("wf_act_bot_talk_custom", WiredEffectBotTalk.class);
        expected.put("wf_act_bot_talk_to_avatar_custom", WiredEffectBotTalkToHabbo.class);
        expected.put("wf_act_call_stacks_custom", WiredEffectTriggerStacks.class);
        expected.put("wf_act_execute_stack_custom", WiredEffectTriggerStacks.class);
        expected.put("wf_act_cnd_move_furni", WiredEffectMoveFurniTo.class);
        expected.put("wf_act_cnd_move_rotate", WiredEffectMoveRotateFurni.class);
        expected.put("wf_act_cnd_toggle_state", WiredEffectToggleFurni.class);
        expected.put("wf_act_freeze_habbo", WiredEffectFreeze.class);
        expected.put("wf_act_unfreeze_habbo", WiredEffectUnfreeze.class);
        expected.put("wf_act_match_to_sshot_new", WiredEffectMatchFurni.class);
        expected.put("wf_act_move_furni_to_furni", WiredEffectFurniToFurni.class);
        expected.put("wf_act_teleport_bots_to_furni", WiredEffectBotTeleport.class);
        expected.put("wf_act_tp_furni_to_habbo", WiredEffectFurniToUser.class);
        // conditions
        expected.put("wf_cnd_habbo_in_group", WiredConditionGroupMember.class);
        expected.put("wf_cnd_not_habbo_in_group", WiredConditionNotInGroup.class);
        expected.put("wf_cnd_match_snapshot_new", WiredConditionMatchStatePosition.class);
        expected.put("wf_cnd_not_match_snap_new", WiredConditionNotMatchStatePosition.class);
        expected.put("wf_cnd_tgr_furni_hv_avtrs", WiredConditionFurniHaveHabbo.class);
        expected.put("wf_cnd_not_tgr_furni_hv_avtrs", WiredConditionNotFurniHaveHabbo.class);
        expected.put("wf_cnd_wears_effect", WiredConditionHabboHasEffect.class);
        expected.put("wf_cnd_not_wears_effect", WiredConditionNotHabboHasEffect.class);
        expected.put("wf_cnd_wears_handitem", WiredConditionHabboHasHandItem.class);
        expected.put("wf_cnd_not_wears_handitem", WiredConditionNotHabboHasHandItem.class);
        expected.put("wf_cnd_trgr_stuff_matches", WiredConditionFurniTypeMatch.class);
        // triggers
        expected.put("wf_trg_cnd_collision", WiredTriggerCollision.class);
        expected.put("wf_trg_user_exits_room", WiredTriggerHabboLeavesRoom.class);

        assertEquals(27, expected.size(), "expected exactly the 27 Phase-A aliases");

        for (Map.Entry<String, Class<?>> e : expected.entrySet()) {
            ItemInteraction resolved = itemManager.getItemInteraction(e.getKey());
            assertSame(e.getValue(), resolved.getType(),
                    "alias '" + e.getKey() + "' must resolve to " + e.getValue().getSimpleName());
        }
    }

    @Test
    void unknownTypeStillFallsBackToDefault() {
        // sanity: an unregistered name must still resolve to InteractionDefault (resolution unchanged)
        assertSame(InteractionDefault.class,
                itemManager.getItemInteraction("wf_act_this_name_does_not_exist").getType());
    }

    @Test
    void canonicalNamesStillResolveAfterAliasing() {
        // the duplicate-class aliases must not disturb the original canonical bindings
        assertSame(WiredEffectFreeze.class, itemManager.getItemInteraction("wf_act_freeze").getType());
        assertSame(WiredConditionGroupMember.class, itemManager.getItemInteraction("wf_cnd_actor_in_group").getType());
    }
}
