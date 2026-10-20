package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniOpacityIs;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboCount;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasHighscorePoints;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasRank;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchStatePosition;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboCount;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionUserLevel;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionUserOnFurniWithState;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * The conditions that kept a source in their ints read it from the official source slot and send only their own
 * params; the source goes out in the official furni or user source field.
 */
class WiredConditionOfficialSourceSlotsTest {
    private static final int[] NONE = new int[0];

    @Test
    void furniOpacitySendsOpacityAndComparisonWithTheFurniInTheSlot() {
        WiredConditionFurniOpacityIs box = new WiredConditionFurniOpacityIs(1, 1, mock(Item.class), "", 0, 0);
        save(box, new int[] {40, WiredComparison.GREATER}, new int[] {WiredSourceUtil.SOURCE_SELECTOR}, NONE);

        assertDefinition(
                box, new int[] {40, WiredComparison.GREATER}, new int[] {WiredSourceUtil.SOURCE_SELECTOR}, NONE);
    }

    @Test
    void userRankAndHighscorePointsSendTheirParamsWithTheUsersAndTheQuantifierInTheSlots() {
        WiredConditionHabboHasRank rank = new WiredConditionHabboHasRank(1, 1, mock(Item.class), "", 0, 0);
        save(rank, new int[] {5, WiredComparison.EQUAL}, 1, NONE, new int[] {WiredSourceUtil.SOURCE_SIGNAL});
        assertDefinition(rank, new int[] {5, WiredComparison.EQUAL}, NONE, new int[] {WiredSourceUtil.SOURCE_SIGNAL});
        assertQuantifier(rank, 1);

        WiredConditionHabboHasHighscorePoints points =
                new WiredConditionHabboHasHighscorePoints(1, 1, mock(Item.class), "", 0, 0);
        save(points, new int[] {30, WiredComparison.LESS}, 0, NONE, new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        assertDefinition(
                points, new int[] {30, WiredComparison.LESS}, NONE, new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        assertQuantifier(points, 0);
    }

    @Test
    void userLevelSendsTheOfficialLevelAndComparisonWithTheQuantifierInItsSlot() {
        WiredConditionUserLevel level = new WiredConditionUserLevel(1, 1, mock(Item.class), "", 0, 0);
        save(level, new int[] {12, WiredConditionUserLevel.OFFICIAL_EQUAL}, 1, NONE, new int[] {
            WiredSourceUtil.SOURCE_SELECTOR
        });

        assertDefinition(level, new int[] {12, WiredConditionUserLevel.OFFICIAL_EQUAL}, NONE, new int[] {
            WiredSourceUtil.SOURCE_SELECTOR
        });
        assertQuantifier(level, 1);
    }

    @Test
    void userCountSendsTheOfficialMinAndMaxOnly() {
        WiredConditionHabboCount count = new WiredConditionHabboCount(1, 1, mock(Item.class), "", 0, 0);
        save(count, new int[] {2, 8}, NONE, new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        assertDefinition(count, new int[] {2, 8}, NONE, new int[] {WiredSourceUtil.SOURCE_SELECTOR});

        WiredConditionNotHabboCount notCount = new WiredConditionNotHabboCount(1, 1, mock(Item.class), "", 0, 0);
        save(notCount, new int[] {3, 9}, NONE, new int[] {WiredSourceUtil.SOURCE_SIGNAL});
        assertDefinition(notCount, new int[] {3, 9}, NONE, new int[] {WiredSourceUtil.SOURCE_SIGNAL});
    }

    @Test
    void furniSnapshotAndUserOnFurniWithStateKeepTheirSourcesInTheSlots() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            WiredConditionMatchStatePosition snapshot =
                    new WiredConditionMatchStatePosition(1, 1, mock(Item.class), "", 0, 0);
            save(snapshot, new int[] {1, 0, 1, 1}, 1, new int[] {WiredSourceUtil.SOURCE_SELECTOR}, NONE);
            assertDefinition(snapshot, new int[] {1, 0, 1, 1}, new int[] {WiredSourceUtil.SOURCE_SELECTOR}, NONE);
            assertQuantifier(snapshot, 1);

            WiredConditionUserOnFurniWithState onFurni =
                    new WiredConditionUserOnFurniWithState(1, 1, mock(Item.class), "", 0, 0);
            save(onFurni, NONE, 1, new int[] {WiredSourceUtil.SOURCE_SIGNAL}, new int[] {WiredSourceUtil.SOURCE_SELECTOR
            });
            assertDefinition(
                    onFurni, NONE, new int[] {WiredSourceUtil.SOURCE_SIGNAL}, new int[] {WiredSourceUtil.SOURCE_SELECTOR
                    });
            assertQuantifier(onFurni, 1);
        }
    }

    private static void save(InteractionWiredCondition box, int[] ints, int[] furniSources, int[] userSources) {
        save(box, ints, 0, furniSources, userSources);
    }

    private static void save(
            InteractionWiredCondition box, int[] ints, int quantifier, int[] furniSources, int[] userSources) {
        WiredSettings settings = new WiredSettings(ints, "", new int[0], -1);
        settings.setQuantifierCode(quantifier);
        settings.setFurniSourceTypes(furniSources);
        settings.setUserSourceTypes(userSources);
        box.saveData(settings);
    }

    private static void assertQuantifier(InteractionWiredCondition box, int quantifier) {
        assertEquals(quantifier, ((WiredOfficialQuantifier) box).officialQuantifierCode(), "quantifier");
    }

    private static void assertDefinition(
            InteractionWiredCondition box, int[] intParams, int[] furniSources, int[] userSources) {
        ServerMessage legacy = new ServerMessage(1);
        box.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
        ByteBuf body = legacy.get();
        body.skipBytes(6);
        WiredDefinitionWire.Definition definition =
                WiredDefinitionWire.readLegacy(body, WiredDefinitionWire.Holder.CONDITION);
        body.release();

        assertArrayEquals(intParams, definition.intParams, "int params");
        if (furniSources.length > 0) assertArrayEquals(furniSources, WiredSourceFields.furniSources(box), "furni");
        if (userSources.length > 0) assertArrayEquals(userSources, WiredSourceFields.userSources(box), "users");
    }
}
