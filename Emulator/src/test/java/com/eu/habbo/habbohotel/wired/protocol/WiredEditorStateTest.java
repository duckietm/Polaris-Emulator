package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.WiredInteractionRegistryFixture;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** Every registered box gives the editor state a wired setup export carries. */
class WiredEditorStateTest {
    @Test
    void everyRegisteredBoxHasAnEditorState() throws Exception {
        List<String> failures = new ArrayList<>();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes()) {
                try {
                    InteractionWired box = WiredInteractionRegistryFixture.instantiate(type);
                    WiredEditorState state = WiredEditorState.of(box, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
                    if (state == null) {
                        failures.add(type.getSimpleName() + " has no editor state");
                        continue;
                    }
                    if (state.holder() != WiredHolders.of(box)) failures.add(type.getSimpleName() + " holder");
                    if (state.stringParam() == null || state.intParams() == null || state.variableIds() == null) {
                        failures.add(type.getSimpleName() + " null field");
                    }
                } catch (Throwable failure) {
                    failures.add(type.getSimpleName() + " " + WiredInteractionRegistryFixture.rootCause(failure));
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void noBoxGivesNoState() {
        assertEquals(null, WiredEditorState.of(null, null));
    }
}
