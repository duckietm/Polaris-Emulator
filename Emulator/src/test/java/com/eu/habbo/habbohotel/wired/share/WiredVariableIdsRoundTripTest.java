package com.eu.habbo.habbohotel.wired.share;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableAgeMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementCurve;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersWithVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Every box that moved its variables into the official variable ids keeps its configuration through the editor's
 * open-then-save round trip (what copy / paste and the setup share do) and through a share code to another room.
 */
class WiredVariableIdsRoundTripTest {
    private final Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);

    WiredVariableIdsRoundTripTest() {
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        RoomUserVariableManager users = mock(RoomUserVariableManager.class);
        RoomFurniVariableManager furni = mock(RoomFurniVariableManager.class);
        RoomVariableManager global = mock(RoomVariableManager.class);
        when(users.getDefinitionInfo(anyInt())).thenReturn(info);
        when(users.hasDefinition(anyInt())).thenReturn(true);
        when(furni.getDefinitionInfo(anyInt())).thenReturn(info);
        when(global.getDefinitionInfo(anyInt())).thenReturn(info);
        when(this.room.getUserVariableManager()).thenReturn(users);
        when(this.room.getFurniVariableManager()).thenReturn(furni);
        when(this.room.getRoomVariableManager()).thenReturn(global);
        when(this.room.getRoomSpecialTypes()).thenReturn(null);
    }

    private interface Body {
        void run() throws Exception;
    }

    private void withEnvironment(Body body) throws Exception {
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(this.room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        when(config.getInt(anyString(), anyInt())).thenReturn(120);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            body.run();
        }
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], -1, 0);
        settings.setVariableIds(variableIds);
        return settings;
    }

    /** The save the editor (and an import) sends back for an opened box: exactly its editor state. */
    private static WiredSettings fromState(WiredEditorState state) {
        WiredSettings settings = new WiredSettings(
                state.intParams().clone(), state.stringParam(), state.stuffIds().clone(), -1, 0);
        settings.setFurniIds2(state.stuffIds2().clone());
        settings.setFurniSourceTypes(state.furniSources().clone());
        settings.setUserSourceTypes(state.userSources().clone());
        settings.setVariableIds(state.variableIds().clone());
        settings.setQuantifierCode(state.quantifierCode());
        settings.setSelectorFilter(state.selectorFilter());
        settings.setSelectorInvert(state.selectorInvert());
        return settings;
    }

    private static boolean save(InteractionWired box, WiredSettings settings) throws Exception {
        if (box instanceof InteractionWiredTrigger trigger) return trigger.saveData(settings, null);
        if (box instanceof InteractionWiredCondition condition) return condition.saveData(settings);
        if (box instanceof InteractionWiredEffect effect) return effect.saveData(settings, null);
        return ((InteractionWiredExtra) box).saveData(settings, null);
    }

    private record Case(String name, Supplier<InteractionWired> box, WiredSettings settings) {}

    private static Item base() {
        return mock(Item.class);
    }

    private static List<Case> cases() {
        WiredSettings change = settings(new int[] {0, 1, 1, 0, 0, -10, 0}, "", "-120", "room:41");
        change.setFurniSourceTypes(new int[] {200, 0});
        change.setUserSourceTypes(new int[] {0, 0});
        WiredSettings valueMatch = settings(new int[] {1, 1, 1, 0, 0, 1, 0}, "", "user:40", "user:41");
        valueMatch.setUserSourceTypes(new int[] {0, 200});
        return List.of(
                new Case(
                        "variable changed",
                        () -> new WiredTriggerVariableChanged(1, 1, base(), "", 0, 0),
                        settings(new int[] {1, 1, 1, 7, -1, 0, 1}, "", "user:40")),
                new Case(
                        "give variable",
                        () -> new WiredEffectGiveVariable(2, 1, base(), "", 0, 0),
                        settings(new int[] {1, 0, 5, 1, 1, -10, 0}, "", "user:40", "room:41")),
                new Case(
                        "remove variable",
                        () -> new WiredEffectRemoveVariable(3, 1, base(), "", 0, 0),
                        settings(new int[] {0}, "", "furni:40")),
                new Case("change variable", () -> new WiredEffectChangeVariableValue(4, 1, base(), "", 0, 0), change),
                new Case(
                        "has variable",
                        () -> new WiredConditionHasVariable(5, 1, base(), "", 0, 0),
                        settings(new int[] {1}, "", "user:40")),
                new Case(
                        "variable value match",
                        () -> new WiredConditionVariableValueMatch(6, 1, base(), "", 0, 0),
                        valueMatch),
                new Case(
                        "variable age match",
                        () -> new WiredConditionVariableAgeMatch(7, 1, base(), "", 0, 0),
                        settings(new int[] {1, 2, 0, 0, 0, 1, 1, -10}, "", "user:40", "room:41")),
                new Case(
                        "users with variable",
                        () -> new WiredEffectUsersWithVariable(8, 1, base(), "", 0, 0),
                        settings(new int[] {2, 2, 0, 0, 1, 0}, "", "-210", "-211")),
                new Case(
                        "selector filter",
                        () -> new WiredExtraFilterFurni(9, 1, base(), "", 0, 0),
                        settings(new int[] {1, 1, -10, 1}, "", "room:41")),
                new Case(
                        "jump strength",
                        () -> new WiredExtraMovementCurve(10, 1, base(), "", 0, 0),
                        settings(new int[] {1, 80, -10, 7, 100}, "", "room:41")));
    }

    @Test
    void openingAndSavingAgainKeepsEveryBoxsVariables() throws Exception {
        this.withEnvironment(() -> {
            for (Case c : cases()) {
                InteractionWired first = c.box().get();
                assertTrue(save(first, c.settings()), c.name());
                WiredEditorState state = WiredEditorState.of(first, this.room);
                assertNotNull(state, c.name());
                assertTrue(state.variableIds().length > 0, c.name());

                InteractionWired second = c.box().get();
                assertTrue(save(second, fromState(state)), c.name());
                WiredEditorState again = WiredEditorState.of(second, this.room);
                assertArrayEquals(state.intParams(), again.intParams(), c.name());
                assertEquals(state.stringParam(), again.stringParam(), c.name());
                assertArrayEquals(state.variableIds(), again.variableIds(), c.name());
                assertArrayEquals(state.furniSources(), again.furniSources(), c.name());
                assertArrayEquals(state.userSources(), again.userSources(), c.name());
                assertArrayEquals(state.stuffIds2(), again.stuffIds2(), c.name());
            }
        });
    }

    @Test
    void aShareCodeMapsTheVariableBoxesToTheImportedOnes() {
        WiredSetupExporter exporter = new WiredSetupExporter(mock(Room.class));
        exporter.boxIndexes.put(40, 0);
        exporter.boxIndexes.put(41, 1);
        List<String> exported = exporter.exportVariables(new String[] {"user:40", "room:41"});
        assertEquals(List.of("user:#0", "room:#1"), exported);

        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(mock(Habbo.class));
        WiredSetupImporter importer = new WiredSetupImporter(mock(Room.class), client, new WiredSetupDocument());
        InteractionWired userVariable = mock(InteractionWired.class);
        when(userVariable.getId()).thenReturn(900);
        importer.resolved.put(0, userVariable);
        // Box 1 was not imported: its slot stays, as "none".
        assertArrayEquals(new String[] {"user:900", "n"}, importer.remapVariables(exported));
        // Built-ins are the same in every room.
        assertArrayEquals(
                new String[] {"-120", "furni:@opacity"},
                importer.remapVariables(exporter.exportVariables(new String[] {"-120", "furni:@opacity"})));
    }
}
