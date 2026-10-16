package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * The official {@code InputSourcesConf} of a wired box: per furni and per user selection slot the sources the editor
 * may offer, the sources a fresh box opens on, and whether wall furni may be picked.
 *
 * <p>The values are the ones the official editor shows for a freshly placed box. A box that is not listed sends an
 * empty conf, and the client keeps its own source rows for it.
 */
public final class WiredInputSourcesConf {
    /** A box without a known conf: no slots, no defaults, floor furni only. */
    public static final WiredInputSourcesConf NONE =
            new WiredInputSourcesConf(new int[0][], new int[0][], new int[0], new int[0], false);

    private static final int[] ALL_FURNI = {0, 100, 101, 200, 201};
    private static final int[] FURNI_NO_SECOND_PICKS = {0, 100, 200, 201};
    private static final int[] FURNI_PICKED_OR_SIGNAL = {0, 100, 201};
    private static final int[] USERS = {0, 200, 201};
    private static final int[] USERS_TRIGGER_OR_SIGNAL = {0, 201};
    private static final int[] BOTS = {0, 100, 200, 201};
    private static final int[] PHYSICS_FURNI = {0, 100, 101, 200, 201, 900};
    private static final int[] PHYSICS_USERS = {0, 200, 201, 900};

    private static final Map<String, WiredInputSourcesConf> BY_INTERACTION = new HashMap<>();

    static {
        WiredInputSourcesConf usersOnly = users(new int[][] {USERS}, 0);
        for (String name : new String[] {
            "wf_act_freeze",
            "wf_act_unfreeze",
            "wf_act_kick_user",
            "wf_act_join_team",
            "wf_act_leave_team",
            "wf_act_give_score",
            "wf_act_move_rotate_user",
            "wf_act_click_conf",
            "wf_xtra_text_output_username",
            "wf_cnd_actor_in_team",
            "wf_cnd_not_in_team",
            "wf_cnd_has_handitem",
            "wf_cnd_not_has_handitem",
            "wf_cnd_actor_in_group",
            "wf_cnd_not_in_group",
            "wf_cnd_user_performs_action",
            "wf_cnd_not_user_performs_action",
            "wf_cnd_actor_dir",
            "wf_cnd_team_has_rank",
            "wf_cnd_team_has_score"
        }) {
            put(name, usersOnly);
        }

        WiredInputSourcesConf pickedFurniOrSignal = furni(new int[][] {FURNI_PICKED_OR_SIGNAL}, 100);
        put(
                pickedFurniOrSignal,
                "wf_slc_users_onfurni",
                "wf_slc_furni_onfurni",
                "wf_slc_furni_bytype",
                "wf_slc_remote");

        WiredInputSourcesConf neighborhood = new WiredInputSourcesConf(
                new int[][] {FURNI_PICKED_OR_SIGNAL},
                new int[][] {USERS_TRIGGER_OR_SIGNAL},
                new int[] {100},
                new int[] {0},
                false);
        put(
                neighborhood,
                "wf_slc_furni_neighborhood",
                "wf_slc_users_neighborhood",
                "wf_xtra_filter_furni",
                "wf_xtra_filter_users",
                "wf_xtra_filter_user");

        put(
                new WiredInputSourcesConf(
                        new int[][] {ALL_FURNI, ALL_FURNI}, new int[0][], new int[] {0, 100}, new int[0], false),
                "wf_cnd_stuff_is",
                "wf_cnd_not_stuff_is");

        put(
                "wf_cnd_slc_quantity",
                new WiredInputSourcesConf(
                        new int[][] {{0, 200, 201}}, new int[][] {USERS}, new int[] {200}, new int[] {200}, false));

        put(
                users(new int[][] {USERS, {0, 101, 200, 201}}, 0, 101),
                "wf_cnd_triggerer_match",
                "wf_cnd_not_triggerer_match");

        WiredInputSourcesConf pickedFurni = furni(new int[][] {FURNI_NO_SECOND_PICKS}, 100);
        put(
                pickedFurni,
                "wf_cnd_match_snapshot",
                "wf_cnd_not_match_snap",
                "wf_act_match_to_sshot",
                "wf_act_adjust_clock",
                "wf_act_control_clock",
                "wf_cnd_counter_time_matches");

        WiredInputSourcesConf furniAndUsers = new WiredInputSourcesConf(
                new int[][] {FURNI_NO_SECOND_PICKS}, new int[][] {USERS}, new int[] {100}, new int[] {0}, false);
        put(
                furniAndUsers,
                "wf_cnd_trggrer_on_frn",
                "wf_cnd_not_trggrer_on",
                "wf_act_furni_to_user",
                "wf_act_user_to_furni",
                "wf_act_teleport_to",
                "wf_act_teleport_to_room");
        put(
                new WiredInputSourcesConf(
                        new int[][] {FURNI_NO_SECOND_PICKS}, new int[][] {USERS}, new int[] {100}, new int[] {0}, true),
                "wf_cnd_has_var",
                "wf_cnd_neg_has_var");

        put(
                "wf_act_move_furni_as_group",
                new WiredInputSourcesConf(
                        new int[][] {ALL_FURNI, ALL_FURNI},
                        new int[][] {USERS},
                        new int[] {100, 0},
                        new int[] {0},
                        true));

        put("wf_act_bot_talk", users(new int[][] {BOTS}, 100));
        put("wf_act_bot_give_handitem", users(new int[][] {BOTS, USERS}, 100, 0));

        put(
                new WiredInputSourcesConf(
                        new int[][] {ALL_FURNI, ALL_FURNI},
                        new int[][] {USERS},
                        new int[] {100, 200},
                        new int[] {200},
                        false),
                "wf_act_send_signal",
                "wf_act_neg_send_signal");

        put(
                "wf_xtra_mov_physics",
                new WiredInputSourcesConf(
                        new int[][] {PHYSICS_FURNI, PHYSICS_FURNI},
                        new int[][] {PHYSICS_USERS},
                        new int[] {900, 900},
                        new int[] {900},
                        false));
        put("wf_xtra_mov_carry_users", users(new int[][] {PHYSICS_USERS}, 900));

        put(
                furni(new int[][] {{100, 200}}, 100),
                "wf_trg_state_changed",
                "wf_trg_stuff_state",
                "wf_trg_walks_off_furni");
        put("wf_trg_bot_reached_avtr", users(new int[][] {{100, 200}}, 100));

        put(
                "wf_act_change_var_val",
                new WiredInputSourcesConf(
                        new int[][] {ALL_FURNI, ALL_FURNI},
                        new int[][] {USERS, USERS},
                        new int[] {0, 200},
                        new int[] {0, 200},
                        true));

        put(
                new WiredInputSourcesConf(
                        new int[][] {ALL_FURNI, ALL_FURNI},
                        new int[][] {USERS, USERS},
                        new int[] {100, 200},
                        new int[] {0, 200},
                        true),
                "wf_act_give_currency",
                "wf_act_give_furni");

        put("wf_act_remove_furni", furni(new int[][] {{0, 200, 201}}, 200));
    }

    private final int[][] allowedFurni;
    private final int[][] allowedUsers;
    private final int[] defaultFurni;
    private final int[] defaultUsers;
    private final boolean allowWallFurni;

    WiredInputSourcesConf(
            int[][] allowedFurni,
            int[][] allowedUsers,
            int[] defaultFurni,
            int[] defaultUsers,
            boolean allowWallFurni) {
        this.allowedFurni = allowedFurni;
        this.allowedUsers = allowedUsers;
        this.defaultFurni = defaultFurni;
        this.defaultUsers = defaultUsers;
        this.allowWallFurni = allowWallFurni;
    }

    private static WiredInputSourcesConf furni(int[][] allowed, int... defaults) {
        return new WiredInputSourcesConf(allowed, new int[0][], defaults, new int[0], false);
    }

    private static WiredInputSourcesConf users(int[][] allowed, int... defaults) {
        return new WiredInputSourcesConf(new int[0][], allowed, new int[0], defaults, false);
    }

    private static void put(String interaction, WiredInputSourcesConf conf) {
        BY_INTERACTION.put(interaction, conf);
    }

    private static void put(WiredInputSourcesConf conf, String... interactions) {
        for (String interaction : interactions) BY_INTERACTION.put(interaction, conf);
    }

    /** The conf of the box's interaction type, or {@link #NONE}. */
    public static WiredInputSourcesConf of(Object box) {
        if (!(box instanceof HabboItem item)) return NONE;
        Item base = item.getBaseItem();
        ItemInteraction interaction = base != null ? base.getInteractionType() : null;
        if (interaction == null || interaction.getName() == null) return NONE;
        return BY_INTERACTION.getOrDefault(interaction.getName().toLowerCase(), NONE);
    }

    /** The conf registered for an interaction name, or {@link #NONE}. */
    public static WiredInputSourcesConf forInteraction(String interaction) {
        return interaction == null ? NONE : BY_INTERACTION.getOrDefault(interaction.toLowerCase(), NONE);
    }

    public int[][] allowedFurni() {
        return copy(this.allowedFurni);
    }

    public int[][] allowedUsers() {
        return copy(this.allowedUsers);
    }

    public int[] defaultFurni() {
        return this.defaultFurni.clone();
    }

    public int[] defaultUsers() {
        return this.defaultUsers.clone();
    }

    public boolean allowWallFurni() {
        return this.allowWallFurni;
    }

    /** Allowed furni, allowed users, default furni, default users, then the wall flag, as the official client reads them. */
    void write(ServerMessage out) {
        writeAllowed(out, this.allowedFurni);
        writeAllowed(out, this.allowedUsers);
        writeInts(out, this.defaultFurni);
        writeInts(out, this.defaultUsers);
        out.appendBoolean(this.allowWallFurni);
    }

    private static void writeAllowed(ServerMessage out, int[][] slots) {
        out.appendInt(slots.length);
        for (int[] slot : slots) writeInts(out, slot);
    }

    private static void writeInts(ServerMessage out, int[] values) {
        out.appendInt(values.length);
        for (int value : values) out.appendInt(value);
    }

    private static int[][] copy(int[][] slots) {
        int[][] copy = new int[slots.length][];
        for (int i = 0; i < slots.length; i++) copy[i] = slots[i].clone();
        return copy;
    }
}
