package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden framed bytes for every current wired outgoing composer family. */
class WiredPacketGoldenCompatibilityTest {

    private static final String REGENERATE_PROPERTY = "polaris.wired.packets.regenerate";
    private static final Path CONTRACT =
            Path.of("src", "test", "resources", "wired-compatibility", "outgoing-packets-v1.txt");

    @Test
    void outgoingWiredPacketsKeepTheirExactFramedBytes() throws Exception {
        List<String> actual = snapshot();
        if (System.getProperty(REGENERATE_PROPERTY) != null) {
            Files.createDirectories(CONTRACT.getParent());
            Files.write(CONTRACT, actual, StandardCharsets.UTF_8);
            return;
        }

        assertTrue(
                Files.isRegularFile(CONTRACT),
                "Missing wired packet golden file; regenerate with -D" + REGENERATE_PROPERTY + "=true");
        assertEquals(
                Files.readAllLines(CONTRACT, StandardCharsets.UTF_8),
                actual,
                "A wired packet's exact header, field order, width or boolean representation changed");
    }

    private static List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        lines.add("# Polaris outgoing wired packet golden bytes v1");
        lines.add("# Lower-case hex includes the four-byte frame length and two-byte header.");
        lines.add("");

        Room editorRoom = mock(Room.class);
        lines.add(packet("trigger-editor", triggerComposer(editorRoom)));
        lines.add(packet("effect-editor", effectComposer(editorRoom)));
        lines.add(packet("condition-editor", conditionComposer(editorRoom)));
        lines.add(packet("extra-editor", extraComposer(editorRoom)));

        HabboItem openItem = mock(HabboItem.class);
        when(openItem.getId()).thenReturn(0x01020304);
        lines.add(packet("open", new WiredOpenComposer(openItem)));
        lines.add(packet("saved", new WiredSavedComposer()));
        lines.add(packet("reward", new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_ITEM)));
        lines.add(packet("monitor-empty", new WiredMonitorDataComposer(null)));
        lines.add(packet("room-settings-empty", new WiredRoomSettingsDataComposer(null, null)));
        lines.add(packet("room-settings-capabilities", roomSettingsComposer()));
        lines.add(packet("variables-empty", new WiredUserVariablesDataComposer(null, null, null, List.of())));
        lines.add(packet(
                "furni-runtime-state", new WiredFurniRuntimeStateComposer(0x01020304, "@gravity", 1, true, false)));
        lines.add(packet("movements-empty", new WiredMovementsComposer(List.of())));
        lines.add(packet("movements-mixed", movementComposer()));
        lines.add(packet(
                "opacity-mixed",
                new WiredFurniOpacityComposer(
                        77,
                        List.of(
                                new WiredOpacityState(0x01020304, false, 35, true),
                                new WiredOpacityState(0x01020305, true, 90, false)),
                        3,
                        1500)));
        return lines;
    }

    private static MessageComposer triggerComposer(Room room) {
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        appendFixtureWhenSerialized(trigger, room);
        return new WiredTriggerDataComposer(trigger, room);
    }

    private static MessageComposer effectComposer(Room room) {
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class);
        appendFixtureWhenSerialized(effect, room);
        return new WiredEffectDataComposer(effect, room);
    }

    private static MessageComposer conditionComposer(Room room) {
        InteractionWiredCondition condition = mock(InteractionWiredCondition.class);
        appendFixtureWhenSerialized(condition, room);
        return new WiredConditionDataComposer(condition, room);
    }

    private static MessageComposer extraComposer(Room room) {
        InteractionWiredExtra extra = mock(InteractionWiredExtra.class);
        appendFixtureWhenSerialized(extra, room);
        return new WiredExtraDataComposer(extra, room);
    }

    private static void appendFixtureWhenSerialized(InteractionWiredTrigger trigger, Room room) {
        doAnswer(invocation -> appendFixture(invocation.getArgument(0)))
                .when(trigger)
                .serializeWiredData(any(ServerMessage.class), eq(room));
    }

    private static void appendFixtureWhenSerialized(InteractionWiredEffect effect, Room room) {
        doAnswer(invocation -> appendFixture(invocation.getArgument(0)))
                .when(effect)
                .serializeWiredData(any(ServerMessage.class), eq(room));
    }

    private static void appendFixtureWhenSerialized(InteractionWiredCondition condition, Room room) {
        doAnswer(invocation -> appendFixture(invocation.getArgument(0)))
                .when(condition)
                .serializeWiredData(any(ServerMessage.class), eq(room));
    }

    private static void appendFixtureWhenSerialized(InteractionWiredExtra extra, Room room) {
        doAnswer(invocation -> appendFixture(invocation.getArgument(0)))
                .when(extra)
                .serializeWiredData(any(ServerMessage.class), eq(room));
    }

    private static Object appendFixture(ServerMessage message) {
        message.appendBoolean(true);
        message.appendInt(42);
        message.appendString("wired");
        return null;
    }

    private static MessageComposer roomSettingsComposer() {
        Room room = mock(Room.class);
        Habbo habbo = mock(Habbo.class);
        when(room.getId()).thenReturn(77);
        when(room.canInspectWired(habbo)).thenReturn(true);
        when(room.canModifyWired(habbo)).thenReturn(false);
        when(room.canManageWiredSettings(habbo)).thenReturn(true);
        when(room.getWiredInspectMask()).thenReturn(0x11);
        when(room.getWiredModifyMask()).thenReturn(0x22);
        return new WiredRoomSettingsDataComposer(room, habbo);
    }

    private static MessageComposer movementComposer() {
        List<WiredMovementsComposer.MovementData> movements = List.of(
                WiredMovementsComposer.furniMovement(42, 1, 2, 3, 4, 0.5, 1.25, 6, 750, 125, 2, 99),
                WiredMovementsComposer.userSlideMovement(7, 4, 3, 2, 1, 1.25, 0.5, 6, 4, 350),
                WiredMovementsComposer.userDirectionUpdate(7, 2, 6),
                WiredMovementsComposer.wallItemMovement(55, true, new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}));
        return new WiredMovementsComposer(movements);
    }

    private static String packet(String name, MessageComposer composer) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            return name + "=" + HexFormat.of().formatHex(bytes);
        } finally {
            packet.release();
        }
    }
}
