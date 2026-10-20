package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniInRange;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboNotHasRights;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasTag;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionLessTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHasTag;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotInTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTeamMember;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

/** The default int params and the condition quantifier footer the official definition carries. */
class WiredEditorDefaultsTest {

    private static Item base(String interaction) {
        Item base = mock(Item.class);
        when(base.getInteractionType()).thenReturn(new ItemInteraction(interaction, HabboItem.class));
        return base;
    }

    @Test
    void listedBoxesSendTheObservedDefaultParams() {
        assertArrayEquals(new int[] {0, 5, 0}, WiredEditorDefaults.forInteraction("wf_cnd_slc_quantity"));
        assertArrayEquals(new int[] {10, 0, 1}, WiredEditorDefaults.forInteraction("wf_xtra_filter_furni"));
        assertArrayEquals(new int[] {20}, WiredEditorDefaults.forInteraction("wf_cnd_time_less_than"));
        assertArrayEquals(new int[] {0, -1, 0}, WiredEditorDefaults.forInteraction("wf_act_bot_talk"));
        assertArrayEquals(new int[0], WiredEditorDefaults.forInteraction("wf_act_toggle_state"));
    }

    @Test
    void aUserConditionReportsItsQuantifierAndTheNegativeLabels() {
        WiredConditionTeamMember team = new WiredConditionTeamMember(1, 1, base("wf_cnd_actor_in_team"), "", 0, 0);
        WiredSettings anyUser = new WiredSettings(new int[] {1}, "", new int[0], 0);
        anyUser.setQuantifierCode(1);
        team.saveData(anyUser);
        WiredConditionNotInTeam notInTeam = new WiredConditionNotInTeam(2, 1, base("wf_cnd_not_in_team"), "", 0, 0);

        assertEquals(WiredEditorDefaults.QUANTIFIER_USERS, WiredEditorDefaults.quantifierType(team));
        assertEquals(1, WiredEditorDefaults.quantifierCode(team));
        assertFalse(WiredEditorDefaults.quantifierInvert(team));
        assertEquals(WiredEditorDefaults.QUANTIFIER_USERS, WiredEditorDefaults.quantifierType(notInTeam));
        assertEquals(0, WiredEditorDefaults.quantifierCode(notInTeam));
        assertTrue(WiredEditorDefaults.quantifierInvert(notInTeam));
    }

    @Test
    void everyQuantifiedConditionGetsTheFooterOfItsFamily() {
        WiredConditionVariableValueMatch value =
                new WiredConditionVariableValueMatch(1, 1, base("wf_cnd_var_val_match"), "", 0, 0);
        WiredConditionNotHasVariable notHasVariable =
                new WiredConditionNotHasVariable(2, 1, base("wf_cnd_neg_has_var"), "", 0, 0);
        WiredConditionHasTag tag = new WiredConditionHasTag(3, 1, base("wf_cnd_has_tag"), "", 0, 0);
        WiredConditionNotHasTag notTag = new WiredConditionNotHasTag(4, 1, base("wf_cnd_not_has_tag"), "", 0, 0);
        WiredConditionFurniInRange range =
                new WiredConditionFurniInRange(5, 1, base("wf_cnd_furni_in_range"), "", 0, 0);
        WiredConditionHabboNotHasRights noRights =
                new WiredConditionHabboNotHasRights(6, 1, base("wf_cnd_not_habbo_has_rights"), "", 0, 0);

        assertEquals(WiredEditorDefaults.QUANTIFIER_VARIABLES, WiredEditorDefaults.quantifierType(value));
        assertFalse(WiredEditorDefaults.quantifierInvert(value));
        // AIR's not-has-variable reads its quantifier as the negative labels.
        assertEquals(WiredEditorDefaults.QUANTIFIER_VARIABLES, WiredEditorDefaults.quantifierType(notHasVariable));
        assertTrue(WiredEditorDefaults.quantifierInvert(notHasVariable));
        assertEquals(WiredEditorDefaults.QUANTIFIER_USERS, WiredEditorDefaults.quantifierType(tag));
        assertTrue(WiredEditorDefaults.quantifierInvert(notTag));
        assertEquals(WiredEditorDefaults.QUANTIFIER_FURNI, WiredEditorDefaults.quantifierType(range));
        // "Lacks rights" quantifies over the users that lack them, so it keeps the plain labels.
        assertEquals(WiredEditorDefaults.QUANTIFIER_USERS, WiredEditorDefaults.quantifierType(noRights));
        assertFalse(WiredEditorDefaults.quantifierInvert(noRights));
    }

    @Test
    void aBoxThatDoesNotReportItsChoiceGetsNoFooter() {
        HabboItem box = mock(HabboItem.class);
        Item base = base("wf_cnd_actor_dir");
        when(box.getBaseItem()).thenReturn(base);

        assertEquals(WiredEditorDefaults.QUANTIFIER_NONE, WiredEditorDefaults.quantifierType(box));
        assertFalse(WiredEditorDefaults.quantifierInvert(box));
        assertArrayEquals(new int[] {0}, WiredEditorDefaults.defaultIntParams(box));
    }

    @Test
    void theConditionDefinitionCarriesTheFooterAndTheDefaults() {
        WiredDefinitionWire.Definition definition = new WiredDefinitionWire.Definition();
        definition.quantifierType = WiredEditorDefaults.QUANTIFIER_USERS;
        definition.quantifierCode = 1;
        definition.quantifierInvert = true;
        definition.defaultIntParams = new int[] {0, 5, 0};
        ServerMessage out = new ServerMessage(1);

        WiredDefinitionWire.writeOfficial(definition, WiredDefinitionWire.Holder.CONDITION, out);

        ByteBuf in = out.get();
        in.skipBytes(6);
        in.readInt(); // furni limit
        skipInts(in); // stuff ids
        skipInts(in); // stuff ids 2
        in.readInt(); // sprite
        in.readInt(); // id
        in.skipBytes(in.readUnsignedShort()); // string param
        skipInts(in); // int params
        assertEquals(0, in.readInt()); // variable ids
        skipInts(in); // furni sources
        skipInts(in); // user sources
        in.readInt(); // code
        assertEquals(1, in.readInt()); // quantifier code
        assertFalse(in.readBoolean()); // advanced mode
        for (int i = 0; i < 4; i++) assertEquals(0, in.readInt()); // empty input sources conf
        assertFalse(in.readBoolean()); // allow wall
        assertEquals(WiredEditorDefaults.QUANTIFIER_USERS, in.readByte());
        assertTrue(in.readBoolean());
        assertEquals(0, in.readInt()); // wired context
        assertEquals(3, in.readInt());
        assertEquals(0, in.readInt());
        assertEquals(5, in.readInt());
        assertEquals(0, in.readInt());
        assertEquals(0, in.readableBytes());
    }

    @Test
    void newSourceRowsFollowTheObservedFrames() {
        WiredInputSourcesConf clickFurni = WiredInputSourcesConf.forInteraction("wf_trg_click_furni");
        assertArrayEquals(new int[] {100, 200}, clickFurni.allowedFurni()[0]);
        assertTrue(clickFurni.allowWallFurni());
        assertTrue(WiredInputSourcesConf.forInteraction("wf_slc_furni_bytype").allowWallFurni());
        assertTrue(WiredInputSourcesConf.forInteraction("wf_trg_state_changed").allowWallFurni());
        assertArrayEquals(
                new int[] {0, 100},
                WiredInputSourcesConf.forInteraction("wf_act_furni_to_furni").defaultFurni());
        assertArrayEquals(
                new int[] {100, 0},
                WiredInputSourcesConf.forInteraction("wf_act_bot_follow_avatar").defaultUsers());
        assertArrayEquals(
                new int[] {110, 0, 200},
                WiredInputSourcesConf.forInteraction("wf_act_place_furni").defaultFurni());
        assertTrue(WiredInputSourcesConf.forInteraction("wf_act_rel_mov").allowWallFurni());
    }

    private static int[] officialIntParams(WiredConditionLessTimeElapsed box) {
        ServerMessage legacy = new ServerMessage(1);
        box.serializeWiredData(legacy, null);
        ServerMessage out = new ServerMessage(2);
        WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.CONDITION, out, box);

        ByteBuf in = out.get();
        try {
            in.skipBytes(6);
            in.readInt(); // furni limit
            skipInts(in); // stuff ids
            skipInts(in); // stuff ids 2
            in.readInt(); // sprite
            in.readInt(); // id
            in.skipBytes(in.readUnsignedShort()); // string param
            int[] params = new int[in.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = in.readInt();
            return params;
        } finally {
            in.release();
        }
    }

    @Test
    void aFreshBoxOpensOnTheDefaultIntParamsAndASavedBoxOnItsOwn() {
        WiredConditionLessTimeElapsed box =
                new WiredConditionLessTimeElapsed(5, 1, base("wf_cnd_time_less_than"), "", 0, 0);
        assertTrue(box.isEditorFresh());
        assertArrayEquals(new int[] {20}, officialIntParams(box));

        box.saveData(new WiredSettings(new int[] {7}, "", new int[0], 0));
        box.markEditorSaved();
        assertArrayEquals(new int[] {7}, officialIntParams(box));

        // A saved box at the slider's lowest value keeps it: only boxes that were never saved get the
        // defaults. AIR's param is the slider value plus one, so 0 half-seconds is 1 on the wire.
        box.saveData(new WiredSettings(new int[] {1}, "", new int[0], 0));
        assertArrayEquals(new int[] {1}, officialIntParams(box));
    }

    @Test
    void aFreshBoxWithoutDefaultsKeepsItsOwnIntParams() {
        WiredConditionLessTimeElapsed box =
                new WiredConditionLessTimeElapsed(6, 1, base("wf_cnd_unlisted_box"), "", 0, 0);
        // Its own 0 half-seconds, sent as AIR's slider value plus one.
        assertArrayEquals(new int[] {1}, officialIntParams(box));
    }

    private static void skipInts(ByteBuf in) {
        int count = in.readInt();
        in.skipBytes(count * 4);
    }
}
