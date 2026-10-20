package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionPuzzleBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionRandomState;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitch;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitchRemoteControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionVendingMachine;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The usage policy the client is sent is the one the server checks, on the floor and on the wall. */
class FurnitureUsagePolicyTest {
    private static final int PAYLOAD_OFFSET = Integer.BYTES + Short.BYTES;

    @Test
    void furniEverybodyMayUseAreSentAsEverybody() {
        for (Class<? extends HabboItem> type : java.util.List.of(
                InteractionTeleport.class,
                InteractionSwitch.class,
                InteractionSwitchRemoteControl.class,
                InteractionVendingMachine.class,
                InteractionInformationTerminal.class,
                InteractionPostIt.class,
                InteractionPuzzleBox.class,
                InteractionRandomState.class)) {
            assertEquals(FurnitureUsagePolicy.EVERYBODY, FurnitureUsagePolicy.of(mock(type)), type.getSimpleName());
        }
    }

    @Test
    void otherFurniAreSentAsRightsOnlyWhenTheyHaveStates() {
        HabboItem lamp = mock(InteractionDefault.class);
        when(lamp.isUsable()).thenReturn(true);
        HabboItem wired = mock(InteractionWiredEffect.class);
        when(wired.isUsable()).thenReturn(true);
        HabboItem chair = mock(InteractionDefault.class);

        assertEquals(FurnitureUsagePolicy.RIGHTS, FurnitureUsagePolicy.of(lamp));
        assertEquals(FurnitureUsagePolicy.RIGHTS, FurnitureUsagePolicy.of(wired));
        assertEquals(FurnitureUsagePolicy.NOBODY, FurnitureUsagePolicy.of(chair));
        assertEquals(FurnitureUsagePolicy.NOBODY, FurnitureUsagePolicy.of(null));
    }

    @Test
    void aWallSwitchIsSentAsUsableByEverybody() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.WALL);
        when(base.getStateCount()).thenReturn(2);
        when(base.getInteractionType()).thenReturn(new ItemInteraction("switch", InteractionSwitch.class));
        InteractionSwitch wallSwitch = new InteractionSwitch(9, 1, base, "0", 0, 0);
        wallSwitch.setWallPosition(":w=1,2 l=3,4 l");

        assertEquals(FurnitureUsagePolicy.EVERYBODY, wallUsagePolicy(wallSwitch));
    }

    @Test
    void aWallLampIsSentAsRightsOnly() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.WALL);
        when(base.getStateCount()).thenReturn(2);
        when(base.getInteractionType()).thenReturn(new ItemInteraction("default", InteractionDefault.class));
        InteractionDefault lamp = new InteractionDefault(9, 1, base, "0", 0, 0);
        lamp.setWallPosition(":w=1,2 l=3,4 l");

        assertEquals(FurnitureUsagePolicy.RIGHTS, wallUsagePolicy(lamp));
    }

    /** id (string), sprite, wall position, state, expiry, then the usage policy. */
    private static int wallUsagePolicy(HabboItem item) {
        ServerMessage message = new ServerMessage(1);
        item.serializeWallData(message);
        ByteBuf buffer = message.get();
        buffer.readerIndex(PAYLOAD_OFFSET);
        skipString(buffer);
        buffer.readInt();
        skipString(buffer);
        skipString(buffer);
        buffer.readInt();
        return buffer.readInt();
    }

    private static void skipString(ByteBuf buffer) {
        buffer.skipBytes(buffer.readUnsignedShort());
    }

    @Test
    void everyFurniPacketSendsThePolicyTheServerChecks() throws Exception {
        for (String composer :
                java.util.List.of("AddFloorItemComposer", "RoomFloorItemsComposer", "FloorItemUpdateComposer")) {
            String source = Files.readString(
                    Path.of("src/main/java/com/eu/habbo/messages/outgoing/rooms/items/" + composer + ".java"));
            assertTrue(source.contains("appendInt(FurnitureUsagePolicy.of("), composer);
        }
        assertTrue(Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/users/HabboItem.java"))
                .contains("serverMessage.appendInt(FurnitureUsagePolicy.of(this));"));
        assertTrue(Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomPlayTestSupport.java"))
                .contains("FurnitureUsagePolicy.of(item) >= FurnitureUsagePolicy.EVERYBODY"));
    }
}
