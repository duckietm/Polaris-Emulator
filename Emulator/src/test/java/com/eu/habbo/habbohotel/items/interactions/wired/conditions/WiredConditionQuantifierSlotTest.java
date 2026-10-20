package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialQuantifier;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * Every quantified condition takes its all / any choice from the save packet's quantifier slot and reports it in the
 * definition's quantifier field; its int params no longer carry it. Stored rows keep the quantifier in their own
 * field, so a box saved before loads to the same choice.
 */
class WiredConditionQuantifierSlotTest {
    private static final int ANY = 1;

    /** A box, the int params it saves in the new layout, and how many it sends back. */
    private record Family(Supplier<InteractionWiredCondition> box, int[] ints, int sentInts) {}

    private static Map<String, Family> families() {
        Map<String, Family> families = new LinkedHashMap<>();
        // AIR's user conditions.
        families.put("effect", family(() -> new WiredConditionHabboHasEffect(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("not effect", family(() -> new WiredConditionNotHabboHasEffect(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("badge", family(() -> new WiredConditionHabboWearsBadge(1, 1, boxBase(), "", 0, 0), 0));
        families.put("hand item", family(() -> new WiredConditionHabboHasHandItem(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("team", family(() -> new WiredConditionTeamMember(1, 1, boxBase(), "", 0, 0), 1, 1));
        families.put("group", family(() -> new WiredConditionGroupMember(1, 1, boxBase(), "", 0, 0), 0));
        families.put("not in group", family(() -> new WiredConditionNotInGroup(1, 1, boxBase(), "", 0, 0), 0));
        families.put(
                "performs action", family(() -> new WiredConditionUserPerformsAction(1, 1, boxBase(), "", 0, 0), 1, 1));
        families.put("triggerer", family(() -> new WiredConditionTriggererMatch(1, 1, boxBase(), "", 0, 0), 2, 1, 0));
        families.put("direction", family(() -> new WiredConditionActorDir(1, 1, boxBase(), "", 0, 0), 1, 3));
        families.put("on furni", family(() -> new WiredConditionTriggerOnFurni(1, 1, boxBase(), "", 0, 0), 0));
        families.put("team rank", family(() -> new WiredConditionTeamHasRank(1, 1, boxBase(), "", 0, 0), 3, 1, 0, 0));
        families.put(
                "team score", family(() -> new WiredConditionTeamHasScore(1, 1, boxBase(), "", 0, 0), 4, 1, 10, 1, 0));
        families.put("level", family(() -> new WiredConditionUserLevel(1, 1, boxBase(), "", 0, 0), 2, 5, 1));
        // AIR's furni conditions.
        families.put(
                "snapshot",
                family(() -> new WiredConditionMatchStatePosition(1, 1, boxBase(), "", 0, 0), 4, 1, 1, 1, 1));
        families.put("stuff is", family(() -> new WiredConditionFurniTypeMatch(1, 1, boxBase(), "", 0, 0), 1, 101));
        families.put("altitude", family(() -> new WiredConditionHasAltitude(1, 1, boxBase(), "", 0, 0), 2, 100, 1));
        families.put(
                "counter time",
                family(() -> new WiredConditionCounterTimeMatches(1, 1, boxBase(), "", 0, 0), 4, 5, 1, 0, 1));
        // AIR's variable conditions.
        families.put("has variable", family(() -> new WiredConditionHasVariable(1, 1, boxBase(), "", 0, 0), 1, 1));
        // Ours.
        families.put("frozen", family(() -> new WiredConditionFrozen(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("tag", family(() -> new WiredConditionHasTag(1, 1, boxBase(), "", 0, 0), 0));
        families.put("motto", family(() -> new WiredConditionMottoContains(1, 1, boxBase(), "", 0, 0), 0));
        families.put("rights", family(() -> new WiredConditionHabboHasRights(1, 1, boxBase(), "", 0, 0), 0));
        families.put(
                "credits", family(() -> new WiredConditionHabboHasCredits(1, 1, boxBase(), "", 0, 0), 4, 1, 0, 10, 0));
        families.put(
                "lacks diamonds",
                family(() -> new WiredConditionHabboLacksDiamonds(1, 1, boxBase(), "", 0, 0), 4, 1, 0, 10, 0));
        families.put("user in range", family(() -> new WiredConditionUserInRange(1, 1, boxBase(), "", 0, 0), 2, 1, 0));
        families.put(
                "furni in range", family(() -> new WiredConditionFurniInRange(1, 1, boxBase(), "", 0, 0), 2, 1, 0));
        families.put("same height", family(() -> new WiredConditionSameHeight(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("owns furni", family(() -> new WiredConditionHabboOwnsFurni(1, 1, boxBase(), "", 0, 0), 1, 0));
        families.put("rank", family(() -> new WiredConditionHabboHasRank(1, 1, boxBase(), "", 0, 0), 2, 3, 0));
        families.put(
                "highscore",
                family(() -> new WiredConditionHabboHasHighscorePoints(1, 1, boxBase(), "", 0, 0), 2, 10, 0));
        families.put(
                "state on furni", family(() -> new WiredConditionUserOnFurniWithState(1, 1, boxBase(), "", 0, 0), 0));
        return families;
    }

    private static Family family(Supplier<InteractionWiredCondition> box, int sentInts, int... ints) {
        return new Family(box, ints, sentInts);
    }

    @Test
    void theSaveTakesTheQuantifierFromItsSlotAndTheDefinitionLeavesItOutOfTheInts() {
        withEmulator(() -> families().forEach((name, family) -> {
            InteractionWiredCondition any = family.box().get();
            any.saveData(settings(family.ints(), ANY));
            assertEquals(ANY, quantifier(any), name + ": any from the slot");
            assertTrue(any.getWiredData().contains("\"quantifier\":1"), name + ": stored in its own field");
            assertEquals(family.sentInts(), definitionInts(any).length, name + ": the ints carry no quantifier");

            // An int where the old layout put the quantifier no longer counts: the slot says "all".
            InteractionWiredCondition all = family.box().get();
            all.saveData(settings(withTrailingAny(family.ints()), 0));
            assertEquals(0, quantifier(all), name + ": all from the slot");
        }));
    }

    @Test
    void storedRowsLoadToTheQuantifierTheyHad() {
        withEmulator(() -> {
            for (Map.Entry<String, Family> entry : families().entrySet()) {
                InteractionWiredCondition saved = entry.getValue().box().get();
                saved.saveData(settings(entry.getValue().ints(), ANY));
                String row = saved.getWiredData();

                InteractionWiredCondition loaded = entry.getValue().box().get();
                loaded.loadWiredData(row(row), mock(Room.class, Answers.RETURNS_DEEP_STUBS));
                assertEquals(ANY, quantifier(loaded), entry.getKey() + ": the stored any survives a reload");
                assertEquals(
                        entry.getValue().sentInts(),
                        definitionInts(loaded).length,
                        entry.getKey() + ": a reloaded box sends no quantifier int");
            }
        });
    }

    @Test
    void rowsWrittenBeforeTheSlotKeepTheirQuantifier() {
        // Rows as earlier builds stored them: the quantifier always had a field of its own.
        withEmulator(() -> {
            assertLoadsAny(
                    new WiredConditionHabboHasEffect(1, 1, boxBase(), "", 0, 0),
                    "{\"effectId\":0,\"userSource\":0,\"quantifier\":1}");
            assertLoadsAny(
                    new WiredConditionHabboWearsBadge(1, 1, boxBase(), "", 0, 0),
                    "{\"badge\":\"ACH1\",\"userSource\":0,\"quantifier\":1}");
            assertLoadsAny(
                    new WiredConditionTeamHasScore(1, 1, boxBase(), "", 0, 0),
                    "{\"teamType\":1,\"comparison\":1,\"score\":10,\"userSource\":0,\"quantifier\":1}");
            assertLoadsAny(
                    new WiredConditionHabboHasRank(1, 1, boxBase(), "", 0, 0),
                    "{\"rank\":3,\"comparison\":1,\"userSource\":0,\"quantifier\":1}");
            assertLoadsAny(
                    new WiredConditionHasTag(1, 1, boxBase(), "", 0, 0),
                    "{\"tag\":\"vip\",\"userSource\":0,\"quantifier\":1}");
        });
    }

    private static void assertLoadsAny(InteractionWiredCondition box, String row) throws SQLException {
        box.loadWiredData(row(row), mock(Room.class, Answers.RETURNS_DEEP_STUBS));
        assertEquals(ANY, quantifier(box), box.getClass().getSimpleName());
    }

    @Test
    void variableAgeAndValueRowsKeepTheirQuantifierOutOfTheInts() {
        withEmulator(WiredConditionQuantifierSlotTest::variableAgeAndValue);
    }

    private static void variableAgeAndValue() throws SQLException {
        WiredConditionVariableAgeMatch age = new WiredConditionVariableAgeMatch(1, 1, boxBase(), "", 0, 0);
        age.loadWiredData(row("{\"variableToken\":\"internal:@level\",\"quantifier\":1}"), null);
        assertEquals(ANY, quantifier(age));
        assertEquals(8, definitionInts(age).length);

        WiredConditionVariableValueMatch value = new WiredConditionVariableValueMatch(1, 1, boxBase(), "", 0, 0);
        value.loadWiredData(row("{\"variableToken\":\"internal:@level\",\"quantifier\":1}"), null);
        assertEquals(ANY, quantifier(value));
        assertEquals(7, definitionInts(value).length);
    }

    @Test
    void aDirectionRowInTheTabbedFormKeepsItsQuantifier() throws SQLException {
        WiredConditionActorDir box = new WiredConditionActorDir(1, 1, boxBase(), "", 0, 0);
        box.loadWiredData(row("5\t0\t1"), null);

        assertEquals(ANY, quantifier(box));
        assertEquals(1, definitionInts(box).length);
    }

    private static int[] withTrailingAny(int[] ints) {
        int[] old = java.util.Arrays.copyOf(ints, ints.length + 1);
        old[ints.length] = ANY;
        return old;
    }

    private static WiredSettings settings(int[] ints, int quantifier) {
        WiredSettings settings = new WiredSettings(ints, "", new int[0], -1);
        settings.setQuantifierCode(quantifier);
        return settings;
    }

    private static int quantifier(InteractionWiredCondition box) {
        return ((WiredOfficialQuantifier) box).officialQuantifierCode();
    }

    /** The int params the definition sends, read back from the box's legacy definition. */
    private static int[] definitionInts(InteractionWiredCondition box) {
        ServerMessage legacy = new ServerMessage(1);
        box.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
        ByteBuf body = legacy.get();
        try {
            body.skipBytes(6); // frame
            body.readBoolean();
            body.readInt(); // furni limit
            skipInts(body); // picks
            body.readInt(); // sprite
            body.readInt(); // id
            body.skipBytes(body.readUnsignedShort()); // string
            int count = body.readInt();
            int[] ints = new int[count];
            for (int i = 0; i < count; i++) ints[i] = body.readInt();
            return ints;
        } finally {
            body.release();
        }
    }

    private static void skipInts(ByteBuf body) {
        int count = body.readInt();
        body.skipBytes(count * 4);
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    /** A test body that may read rows. */
    private interface Body {
        void run() throws Exception;
    }

    private static void withEmulator(Body body) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));
            body.run();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
