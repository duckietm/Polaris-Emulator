package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The editor data the official server sends with a box's definition besides its sources: the int params the
 * editor's "reset to default" restores ({@code defaultIntParams}) and, on conditions, the quantifier footer (furni,
 * users or variables, and whether its labels read as the negative form).
 *
 * <p>The values are the ones seen in Habbo's own definitions (AIR WIN63-202610131318); our own quantified boxes are
 * listed with the family they quantify over. A box that is not listed sends no default params and no quantifier. Behaviour observed in plus-emulator/PlusEMU 672c338f, 7e4083da and
 * 1f18e4b6, reimplemented.
 */
public final class WiredEditorDefaults {
    /** Quantifier footer types, as the official condition definition writes them. */
    public static final int QUANTIFIER_NONE = 0;

    public static final int QUANTIFIER_FURNI = 1;
    public static final int QUANTIFIER_USERS = 2;
    public static final int QUANTIFIER_VARIABLES = 3;

    private static final int[] NONE = new int[0];
    private static final Map<String, int[]> DEFAULT_INT_PARAMS = new HashMap<>();
    private static final Map<String, Integer> QUANTIFIER_TYPES = new HashMap<>();
    /**
     * The "not" twins whose result is the positive box's answer for the same quantifier, turned around, so the
     * {@code .neg} labels ("at least one does not match" / "none match") say what they do. Ours add the twins of
     * our own boxes that work the same way and always drew the negative labels.
     */
    private static final Set<String> NEGATIVE_CONDITIONS = Set.of(
            "wf_cnd_not_match_snap",
            "wf_cnd_not_stuff_is",
            "wf_cnd_not_in_team",
            "wf_cnd_not_in_group",
            "wf_cnd_not_habbo_in_group",
            "wf_cnd_not_has_handitem",
            "wf_cnd_not_wearing_fx",
            "wf_cnd_not_wearing_b",
            "wf_cnd_not_user_performs_action",
            "wf_cnd_not_bot_is_dancing",
            "wf_cnd_not_triggerer_match",
            "wf_cnd_not_trg_by_user",
            "wf_cnd_not_trggrer_on",
            "wf_cnd_neg_has_var",
            // Ours.
            "wf_cnd_not_freeze",
            "wf_cnd_not_has_tag",
            "wf_cnd_not_habbo_owns_badge");

    static {
        defaults(new int[] {0, 0, 0, 0}, "wf_act_match_to_sshot", "wf_cnd_match_snapshot", "wf_cnd_not_match_snap");
        defaults(new int[] {0, 0}, "wf_act_move_rotate", "wf_act_rel_mov");
        defaults(new int[] {20}, "wf_cnd_time_less_than", "wf_cnd_time_more_than");
        defaults(new int[] {2, 0, 0, 0, -10, 0}, "wf_slc_furni_with_var", "wf_slc_users_with_var");
        defaults(new int[] {10, 0, 1}, "wf_xtra_filter_furni", "wf_xtra_filter_users", "wf_xtra_filter_user");
        defaults(new int[] {10, 0, 0, 1}, "wf_xtra_filter_furni_by_var", "wf_xtra_filter_users_by_var");
        defaults(new int[] {0, 5, 0}, "wf_cnd_slc_quantity");
        defaults(new int[] {0, 0}, "wf_act_send_signal", "wf_act_neg_send_signal");
        defaults(new int[] {0, -1, 0}, "wf_act_bot_talk", "wf_act_bot_talk_to_avatar");
        defaults(new int[] {1}, "wf_act_bot_follow_avatar");
        defaults(new int[] {2}, "wf_act_bot_give_handitem");
        defaults(new int[] {1, 0, 0, 0, 0, -10, 0}, "wf_act_change_var_val", "wf_act_give_var");
        defaults(new int[] {1, 2, 0, 0, 0, -10, 0}, "wf_cnd_var_val_match");
        defaults(new int[] {1, 0, 0, 0, 0, 1, 0, -10}, "wf_cnd_var_age_match");
        defaults(new int[] {0, 160, -20}, "wf_xtra_mov_curve");
        defaults(new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, -20}, "wf_act_place_furni");
        defaults(new int[] {0}, "wf_cnd_actor_dir");
        defaults(new int[] {2}, "wf_cnd_has_handitem", "wf_cnd_not_has_handitem");
        defaults(new int[] {1}, "wf_cnd_triggerer_match", "wf_cnd_not_triggerer_match");
        defaults(new int[] {0, 0}, "wf_cnd_has_altitude", "wf_cnd_team_has_rank", "wf_cnd_date_rng_active");
        defaults(new int[] {0, 0, 0}, "wf_cnd_team_has_score");
        defaults(new int[] {0, 0, 0, 0}, "wf_cnd_counter_time_matches");
        defaults(new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0}, "wf_cnd_match_time");
        defaults(new int[] {0, 0, 127, 1, 1, 4095, 0, 0}, "wf_cnd_match_date");
        // AIR class_4135: normal mode, multiplier 1, timeout off at 300 s; class_4137: specific contract.
        defaults(new int[] {0, 1, 0, -10, 0, 300}, "wf_act_init_transaction");
        defaults(new int[] {0}, "wf_act_cancel_transaction");
        // AIR class_4234: payment and reward off, coins, a fixed amount of 1 on the room.
        defaults(new int[] {0, 0, 0, 1, -10, 0, 0, 0, 1, -10}, "wf_xtra_custom_contract");

        quantifier(
                QUANTIFIER_FURNI,
                "wf_cnd_match_snapshot",
                "wf_cnd_not_match_snap",
                "wf_cnd_stuff_is",
                "wf_cnd_not_stuff_is",
                "wf_cnd_has_altitude",
                "wf_cnd_counter_time_matches");
        quantifier(
                QUANTIFIER_USERS,
                "wf_cnd_actor_dir",
                "wf_cnd_actor_in_group",
                "wf_cnd_not_in_group",
                "wf_cnd_actor_in_team",
                "wf_cnd_not_in_team",
                "wf_cnd_has_handitem",
                "wf_cnd_not_has_handitem",
                "wf_cnd_wearing_effect",
                "wf_cnd_not_wearing_fx",
                "wf_cnd_wearing_badge",
                "wf_cnd_not_wearing_b",
                "wf_cnd_user_performs_action",
                "wf_cnd_not_user_performs_action",
                "wf_cnd_triggerer_match",
                "wf_cnd_not_triggerer_match",
                "wf_cnd_trggrer_on_frn",
                "wf_cnd_not_trggrer_on",
                "wf_cnd_team_has_rank",
                "wf_cnd_team_has_score",
                "wf_cnd_user_level",
                // The other names our server registers for the same boxes.
                "wf_cnd_habbo_in_group",
                "wf_cnd_not_habbo_in_group",
                "wf_cnd_not_bot_is_dancing",
                "wf_cnd_trg_by_user",
                "wf_cnd_not_trg_by_user");
        quantifier(
                QUANTIFIER_VARIABLES,
                "wf_cnd_has_var",
                "wf_cnd_neg_has_var",
                "wf_cnd_var_val_match",
                "wf_cnd_var_age_match");

        // Our own quantified boxes take the footer of the family they quantify over.
        quantifier(
                QUANTIFIER_FURNI,
                "wf_cnd_furni_in_range",
                "wf_cnd_furni_not_in_range",
                "wf_cnd_has_same_height",
                "wf_cnd_not_has_same_height",
                "wf_cnd_habbo_owns_furni",
                "wf_cnd_habbo_not_owns_furni");
        quantifier(
                QUANTIFIER_USERS,
                "wf_cnd_freeze",
                "wf_cnd_not_freeze",
                "wf_cnd_has_tag",
                "wf_cnd_not_has_tag",
                "wf_cnd_motto_contains",
                "wf_cnd_habbo_has_rights",
                "wf_cnd_not_habbo_has_rights",
                "wf_cnd_habbo_is_male",
                "wf_cnd_habbo_is_female",
                "wf_cnd_habbo_owns_badge",
                "wf_cnd_not_habbo_owns_badge",
                "wf_cnd_habbo_has_credits",
                "wf_cnd_habbo_has_duckets",
                "wf_cnd_habbo_has_at_least_x_items",
                "wf_cnd_not_habbo_has_credits",
                "wf_cnd_not_habbo_has_diamonds",
                "wf_cnd_not_habbo_has_duckets",
                "wf_cnd_user_in_range",
                "wf_cnd_user_not_in_range",
                "wf_cnd_user_on_furni_with_state",
                "wf_cnd_x_points_leaderboard",
                "wf_cnd_not_x_points_leaderboard",
                "wf_cnd_habbo_has_rank",
                "wf_cnd_not_habbo_has_rank");
        quantifier(QUANTIFIER_VARIABLES, "wf_cnd_check_array");
    }

    private WiredEditorDefaults() {}

    private static void defaults(int[] values, String... interactions) {
        for (String interaction : interactions) DEFAULT_INT_PARAMS.put(interaction, values);
    }

    private static void quantifier(int type, String... interactions) {
        for (String interaction : interactions) QUANTIFIER_TYPES.put(interaction, type);
    }

    /** The int params the official editor resets the box to; empty for a box that is not listed. */
    public static int[] defaultIntParams(Object box) {
        return forInteraction(interactionOf(box));
    }

    /** The default int params registered for an interaction name; empty when none are. */
    public static int[] forInteraction(String interaction) {
        if (interaction == null) return NONE;
        int[] values = DEFAULT_INT_PARAMS.get(interaction.toLowerCase(Locale.ROOT));
        return values != null ? values.clone() : NONE;
    }

    /**
     * The condition's quantifier footer type. Only a box that reports its own all / any choice gets one, so the footer
     * never shows a choice the box does not hold.
     */
    public static int quantifierType(Object box) {
        if (!(box instanceof WiredOfficialQuantifier)) return QUANTIFIER_NONE;
        String interaction = interactionOf(box);
        if (interaction == null) return QUANTIFIER_NONE;
        return QUANTIFIER_TYPES.getOrDefault(interaction.toLowerCase(Locale.ROOT), QUANTIFIER_NONE);
    }

    /** The box's all (0) / any (1) choice, for the footer; 0 for a box without a quantifier. */
    public static int quantifierCode(Object box) {
        if (quantifierType(box) == QUANTIFIER_NONE) return 0;
        return ((WiredOfficialQuantifier) box).officialQuantifierCode() == 1 ? 1 : 0;
    }

    /** Whether the footer reads as the negative form ("none of" instead of "all of"). */
    public static boolean quantifierInvert(Object box) {
        if (quantifierType(box) == QUANTIFIER_NONE) return false;
        return NEGATIVE_CONDITIONS.contains(interactionOf(box).toLowerCase(Locale.ROOT));
    }

    private static String interactionOf(Object box) {
        if (!(box instanceof HabboItem item)) return null;
        Item base = item.getBaseItem();
        ItemInteraction interaction = base != null ? base.getInteractionType() : null;
        return interaction != null ? interaction.getName() : null;
    }
}
