package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.*;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboIdles;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboLeavesRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboStartsDancing;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboStopsDancing;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboUnidles;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerUsernameAsTrigger;
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
    void parameterVariantsResolveToTheirBaseClass() {
        Map<String, Class<?>> expected = new LinkedHashMap<>();
        // effects
        expected.put("wf_act_give_score_custom", WiredEffectGiveScore.class);
        expected.put("wf_act_lower_furni", WiredEffectSetAltitude.class);
        expected.put("wf_act_raise_furni", WiredEffectSetAltitude.class);
        expected.put("wf_act_match_to_sshot_height", WiredEffectMatchFurni.class);
        expected.put("wf_act_match_to_sshot_height_instant", WiredEffectMatchFurni.class);
        expected.put("wf_act_plus_match_furni_state", WiredEffectMatchFurni.class);
        expected.put("wf_act_move_rotate_collide", WiredEffectMoveRotateFurni.class);
        expected.put("wf_act_move_rotate_diagonal", WiredEffectMoveRotateFurni.class);
        expected.put("wf_act_rotate_habbo", WiredEffectMoveRotateUser.class);
        expected.put("wf_act_show_message_room", WiredEffectWhisper.class);
        expected.put("wf_act_toggle_state_down", WiredEffectToggleFurni.class);
        expected.put("wf_act_toggle_state_trg", WiredEffectToggleFurni.class);
        // conditions
        expected.put("wf_cnd_atleast_one_user_in_team", WiredConditionTeamMember.class);
        expected.put("wf_cnd_bot_is_dancing", WiredConditionUserPerformsAction.class);
        expected.put("wf_cnd_habbo_is_dancing", WiredConditionUserPerformsAction.class);
        expected.put("wf_cnd_not_habbo_is_dancing", WiredConditionNotUserPerformsAction.class);
        expected.put("wf_cnd_furni_state_pattern", WiredConditionMatchStatePosition.class);
        expected.put("wf_cnd_is_state", WiredConditionMatchStatePosition.class);
        expected.put("wf_cnd_trg_state_is", WiredConditionMatchStatePosition.class);
        expected.put("wf_cnd_furnis_hv_avtrs_custom", WiredConditionFurniHaveHabbo.class);
        expected.put("wf_cnd_x_habbos_on_furni", WiredConditionFurniHaveHabbo.class);
        // triggers
        expected.put("wf_trg_exact_keyword", WiredTriggerHabboSaysKeyword.class);
        expected.put("wf_trg_says_command", WiredTriggerHabboSaysKeyword.class);
        expected.put("wf_trg_habbo_says_command", WiredTriggerHabboSaysKeyword.class);
        expected.put("wf_trg_other_collides_user", WiredTriggerCollision.class);
        expected.put("wf_trg_user_collides_bot", WiredTriggerCollision.class);
        expected.put("wf_trg_user_collides_other", WiredTriggerCollision.class);
        // extra
        expected.put("wf_xtra_one_condition", WiredExtraOrEval.class);

        assertEquals(28, expected.size(), "expected exactly the 28 Phase-A parameter-variants");

        for (Map.Entry<String, Class<?>> e : expected.entrySet()) {
            ItemInteraction resolved = itemManager.getItemInteraction(e.getKey());
            assertSame(e.getValue(), resolved.getType(),
                    "variant '" + e.getKey() + "' must resolve to " + e.getValue().getSimpleName());
        }
    }

    @Test
    void phaseBDanceIdleTriggersResolve() {
        // Phase B — new trigger classes for events that already fire from RoomUnitManager/RoomCycleManager.
        assertSame(WiredTriggerHabboStartsDancing.class, itemManager.getItemInteraction("wf_trg_starts_dancing").getType());
        assertSame(WiredTriggerHabboStopsDancing.class, itemManager.getItemInteraction("wf_trg_stops_dancing").getType());
        assertSame(WiredTriggerHabboIdles.class, itemManager.getItemInteraction("wf_trg_idles").getType());
        assertSame(WiredTriggerHabboUnidles.class, itemManager.getItemInteraction("wf_trg_unidles").getType());
    }

    @Test
    void phaseCCurrencyEffectsResolve() {
        // Phase C — new currency effects (reuse the SHOW_MESSAGE dialog code; no new client dialog).
        assertSame(WiredEffectGiveCredits.class, itemManager.getItemInteraction("wf_act_give_credits").getType());
        assertSame(WiredEffectGiveDuckets.class, itemManager.getItemInteraction("wf_act_give_duckets").getType());
        assertSame(WiredEffectGiveDiamonds.class, itemManager.getItemInteraction("wf_act_give_diamonds").getType());
    }

    @Test
    void phaseCCanDoNowEffectsResolve() {
        // Phase C — new effect classes that reuse existing dialog codes (no new client furnidata).
        assertSame(WiredEffectGiveBadge.class, itemManager.getItemInteraction("wf_act_give_badge").getType());
        assertSame(WiredEffectGiveBadge.class, itemManager.getItemInteraction("wf_act_give_userbadge").getType());
        assertSame(WiredEffectRemoveBadge.class, itemManager.getItemInteraction("wf_act_remove_badge").getType());
        assertSame(WiredEffectGiveAchievement.class, itemManager.getItemInteraction("wf_act_give_achievement").getType());
        assertSame(WiredEffectGiveExperience.class, itemManager.getItemInteraction("wf_act_give_experience").getType());
        assertSame(WiredEffectSayCommand.class, itemManager.getItemInteraction("wf_act_say_command").getType());
        assertSame(WiredEffectOpenHabboPages.class, itemManager.getItemInteraction("wf_act_open_habbo_pages").getType());
        assertSame(WiredEffectMakeUserSay.class, itemManager.getItemInteraction("wf_act_make_user_say").getType());
        assertSame(WiredEffectLog.class, itemManager.getItemInteraction("wf_act_log").getType());
        assertSame(WiredEffectWalkToFurni.class, itemManager.getItemInteraction("wf_act_walk_to_furni").getType());
        assertSame(WiredEffectSit.class, itemManager.getItemInteraction("wf_act_sit").getType());
        assertSame(WiredEffectLay.class, itemManager.getItemInteraction("wf_act_lay").getType());
        assertSame(WiredEffectMakeFastWalk.class, itemManager.getItemInteraction("wf_act_make_fast_walk").getType());
        assertSame(WiredEffectToggleMoodlight.class, itemManager.getItemInteraction("wf_act_toggle_moodlight").getType());
        assertSame(WiredEffectResetHighscores.class, itemManager.getItemInteraction("wf_act_reset_highscores").getType());
        assertSame(WiredEffectMoveUserTiles.class, itemManager.getItemInteraction("wf_act_move_user_tiles").getType());
        assertSame(WiredEffectAddTag.class, itemManager.getItemInteraction("wf_act_add_tag").getType());
        assertSame(WiredEffectAddTag.class, itemManager.getItemInteraction("wf_act_add_tag_perm").getType());
        assertSame(WiredEffectRemoveTag.class, itemManager.getItemInteraction("wf_act_remove_tag").getType());
        assertSame(WiredEffectGiveLook.class, itemManager.getItemInteraction("wf_act_give_look").getType());
        assertSame(WiredEffectNegativeShowMessage.class, itemManager.getItemInteraction("wf_act_neg_show_message").getType());
        assertSame(WiredEffectNegativeLog.class, itemManager.getItemInteraction("wf_act_neg_log").getType());
        assertSame(WiredEffectAllUsersLeaveTeam.class, itemManager.getItemInteraction("wf_act_all_users_leave_team").getType());
    }

    @Test
    void phaseCCanDoNowConditionsResolve() {
        // Phase C — new condition classes that reuse existing condition dialog codes.
        assertSame(WiredConditionHabboLacksCredits.class, itemManager.getItemInteraction("wf_cnd_not_habbo_has_credits").getType());
        assertSame(WiredConditionHabboLacksDiamonds.class, itemManager.getItemInteraction("wf_cnd_not_habbo_has_diamonds").getType());
        assertSame(WiredConditionHabboLacksDuckets.class, itemManager.getItemInteraction("wf_cnd_not_habbo_has_duckets").getType());
        assertSame(WiredConditionFrozen.class, itemManager.getItemInteraction("wf_cnd_freeze").getType());
        assertSame(WiredConditionNotFrozen.class, itemManager.getItemInteraction("wf_cnd_not_freeze").getType());
        assertSame(WiredConditionFurniInRange.class, itemManager.getItemInteraction("wf_cnd_furni_in_range").getType());
        assertSame(WiredConditionFurniNotInRange.class, itemManager.getItemInteraction("wf_cnd_furni_not_in_range").getType());
        assertSame(WiredConditionSameHeight.class, itemManager.getItemInteraction("wf_cnd_has_same_height").getType());
        assertSame(WiredConditionNotSameHeight.class, itemManager.getItemInteraction("wf_cnd_not_has_same_height").getType());
        assertSame(WiredConditionHabboOwnsBadge.class, itemManager.getItemInteraction("wf_cnd_habbo_owns_badge").getType());
        assertSame(WiredConditionNotHabboOwnsBadge.class, itemManager.getItemInteraction("wf_cnd_not_habbo_owns_badge").getType());
        assertSame(WiredConditionMottoContains.class, itemManager.getItemInteraction("wf_cnd_motto_contains").getType());
        assertSame(WiredConditionHabboHasMinItems.class, itemManager.getItemInteraction("wf_cnd_habbo_has_at_least_x_items").getType());
        assertSame(WiredConditionHasTag.class, itemManager.getItemInteraction("wf_cnd_has_tag").getType());
        assertSame(WiredConditionNotHasTag.class, itemManager.getItemInteraction("wf_cnd_not_has_tag").getType());
        assertSame(WiredConditionHabboOwnsFurni.class, itemManager.getItemInteraction("wf_cnd_habbo_owns_furni").getType());
        assertSame(WiredConditionHabboNotOwnsFurni.class, itemManager.getItemInteraction("wf_cnd_habbo_not_owns_furni").getType());
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

    @Test
    void groupAPreviouslyInertWiredNowResolve() {
        // Group A — wired furni that loaded as InteractionDefault are now functional via faithful
        // aliases or minimal condition subclasses (no new client dialog required).
        assertSame(WiredEffectMoveFurniAway.class, itemManager.getItemInteraction("wf_act_dont_chase").getType());
        assertSame(WiredEffectMoveFurniAway.class, itemManager.getItemInteraction("wf_act_dont_chase_top").getType());
        assertSame(WiredEffectGiveScore.class, itemManager.getItemInteraction("wf_act_give_score_room").getType());
        assertSame(WiredEffectGiveScore.class, itemManager.getItemInteraction("wf_act_give_score_pp").getType());
        assertSame(WiredEffectBotGiveHandItem.class, itemManager.getItemInteraction("wf_act_bot_give_handitem_or_effect").getType());
        assertSame(WiredEffectTeleport.class, itemManager.getItemInteraction("wf_act_teleport_all").getType());
        assertSame(WiredEffectTeleport.class, itemManager.getItemInteraction("wf_act_teleport_red").getType());
        assertSame(WiredEffectTeleport.class, itemManager.getItemInteraction("wf_act_teleport_green").getType());
        assertSame(WiredEffectTeleport.class, itemManager.getItemInteraction("wf_act_teleport_blue").getType());
        assertSame(WiredEffectTeleport.class, itemManager.getItemInteraction("wf_act_teleport_yellow").getType());
        assertSame(WiredConditionHabboIsMale.class, itemManager.getItemInteraction("wf_cnd_habbo_is_male").getType());
        assertSame(WiredConditionHabboIsFemale.class, itemManager.getItemInteraction("wf_cnd_habbo_is_female").getType());
        assertSame(WiredConditionHabboHasRights.class, itemManager.getItemInteraction("wf_cnd_habbo_has_rights").getType());
        assertSame(WiredConditionHabboNotHasRights.class, itemManager.getItemInteraction("wf_cnd_not_habbo_has_rights").getType());
    }

    @Test
    void groupBBatch1ReuseExistingClientDialogResolve() {
        // Group B batch 1 — inert furni made functional by reusing an existing client dialog
        // (faithful aliases to existing wired classes; no new Nitro dialog required).
        assertSame(WiredEffectMatchFurni.class, itemManager.getItemInteraction("wf_act_set_state").getType());
        assertSame(WiredEffectMatchFurni.class, itemManager.getItemInteraction("wf_act_set_trg_state").getType());
        assertSame(WiredEffectMatchFurni.class, itemManager.getItemInteraction("wf_act_open_gates").getType());
        assertSame(WiredEffectToggleFurni.class, itemManager.getItemInteraction("wf_act_close_dice").getType());
        assertSame(WiredEffectToggleFurni.class, itemManager.getItemInteraction("wf_act_close_gates").getType());
        assertSame(WiredEffectToggleFurni.class, itemManager.getItemInteraction("wf_act_color_furni").getType());
        assertSame(WiredEffectMoveRotateFurni.class, itemManager.getItemInteraction("wf_act_move_furni_from_stack").getType());
        assertSame(WiredEffectMoveRotateFurni.class, itemManager.getItemInteraction("wf_act_move_rotate_no_under").getType());
        assertSame(WiredEffectChangeFurniDirection.class, itemManager.getItemInteraction("wf_act_allign_furni_stack").getType());
        assertSame(WiredEffectTriggerStacks.class, itemManager.getItemInteraction("wf_act_execute_for_users").getType());
        assertSame(WiredConditionTriggererMatch.class, itemManager.getItemInteraction("wf_cnd_trg_by_user").getType());
        assertSame(WiredConditionNotTriggererMatch.class, itemManager.getItemInteraction("wf_cnd_not_trg_by_user").getType());
        assertSame(WiredConditionNotUserPerformsAction.class, itemManager.getItemInteraction("wf_cnd_not_bot_is_dancing").getType());
        assertSame(WiredTriggerHabboClicksUser.class, itemManager.getItemInteraction("wf_trg_click_bot").getType());
        assertSame(WiredTriggerHabboClicksFurni.class, itemManager.getItemInteraction("wf_trg_double_click_furni").getType());
        assertSame(WiredTriggerHabboUnidles.class, itemManager.getItemInteraction("wf_trg_anti_afk").getType());
        assertSame(WiredExtraOrEval.class, itemManager.getItemInteraction("wf_xtra_condition_change").getType());
    }

    @Test
    void groupBBatch2ReuseAndNewClassesResolve() {
        // Group B batch 2 — faithful aliases + new minimal classes reusing an existing client dialog code.
        assertSame(WiredEffectMakeUserSay.class, itemManager.getItemInteraction("wf_act_send_bubble").getType());
        assertSame(WiredEffectToggleFurni.class, itemManager.getItemInteraction("wf_act_double_click").getType());
        assertSame(WiredEffectGiveEffect.class, itemManager.getItemInteraction("wf_act_give_enable").getType());
        assertSame(WiredEffectForwardUserToRoom.class, itemManager.getItemInteraction("wf_act_forward_user_to_room").getType());
        assertSame(WiredConditionUserInRange.class, itemManager.getItemInteraction("wf_cnd_user_in_range").getType());
        assertSame(WiredConditionUserNotInRange.class, itemManager.getItemInteraction("wf_cnd_user_not_in_range").getType());
        assertSame(WiredTriggerUsernameAsTrigger.class, itemManager.getItemInteraction("wf_trg_username_as_trigger").getType());
    }

    @Test
    void groupBNitroDialogTypesResolve() {
        // Group B — types that needed a NEW Nitro dialog (new server code + matching client LayoutCode).
        assertSame(WiredEffectSetRollerSpeed.class, itemManager.getItemInteraction("wf_act_roller_speed").getType());
        assertSame(WiredEffectBotDance.class, itemManager.getItemInteraction("wf_act_bot_start_dance").getType());
        assertSame(WiredEffectBotDance.class, itemManager.getItemInteraction("wf_act_bot_stop_dance").getType());
        assertSame(WiredEffectGivePointsType.class, itemManager.getItemInteraction("wf_act_give_points_type").getType());
        assertSame(WiredConditionNoBattleBanzaiRunning.class, itemManager.getItemInteraction("wf_cnd_not_battlebanzai").getType());
        assertSame(WiredConditionNoBattleBanzaiRunning.class, itemManager.getItemInteraction("wf_cnd_not_battlebz").getType());
        assertSame(WiredEffectGiveOrTakeFurni.class, itemManager.getItemInteraction("wf_act_give_or_take_furni").getType());
        assertSame(WiredEffectPlayYoutube.class, itemManager.getItemInteraction("wf_act_play_youtube_sound").getType());
        assertSame(WiredEffectQuickBopper.class, itemManager.getItemInteraction("wf_act_quick_bopper").getType());
        assertSame(WiredEffectSetRoomAd.class, itemManager.getItemInteraction("wf_act_set_room_ad").getType());
        assertSame(WiredConditionUserOnFurniWithState.class, itemManager.getItemInteraction("wf_cnd_user_on_furni_with_state").getType());
        assertSame(WiredConditionTriggerFurniAdjacentState.class, itemManager.getItemInteraction("wf_cnd_trg_frn_adjacent_state").getType());
    }
}
