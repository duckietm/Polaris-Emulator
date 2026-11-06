package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.floorplaneditor.FloorPlanEditorRequestDoorSettingsEvent;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.guilds.GuildMemberUpdateComposer;
import polaris.messages.outgoing.guilds.GuildRefreshMembersListComposer;
import polaris.messages.outgoing.rooms.pets.breeding.PetBreedingStartFailedComposer;

class PacketReceiverCompatibilityTest {
    @Test
    void floorEditorReceivesCurrentThicknessWithoutChangingLegacyResponse() throws Exception {
        GameClient client = clientInRoom(mock(Room.class));
        Room room = client.getUser().getUserInfo().getCurrentRoom();
        when(room.isHideWall()).thenReturn(true);
        when(room.getWallSize()).thenReturn(-2);
        when(room.getFloorSize()).thenReturn(1);

        FloorPlanEditorRequestDoorSettingsEvent event = new FloorPlanEditorRequestDoorSettingsEvent();
        event.client = client;
        event.handle();

        ArgumentCaptor<MessageComposer> responses = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client, times(3)).sendResponse(responses.capture());
        assertThickness(responses.getAllValues().get(1), Outgoing.RoomFloorThicknessUpdatedComposer, 1, -2);
        assertThickness(responses.getAllValues().get(2), Outgoing.RoomThicknessComposer, -2, 1);
    }

    @Test
    void floorEditorWithoutRoomSendsNothing() throws Exception {
        GameClient client = clientInRoom(null);
        FloorPlanEditorRequestDoorSettingsEvent event = new FloorPlanEditorRequestDoorSettingsEvent();
        event.client = client;
        event.handle();

        verify(client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void groupMemberUpdatePreservesIdentityRankAndUtf8FieldBoundaries() {
        Guild guild = mock(Guild.class);
        GuildMember member = mock(GuildMember.class);
        when(guild.getId()).thenReturn(101);
        when(member.getRank()).thenReturn(GuildRank.ADMIN);
        when(member.getUserId()).thenReturn(202);
        when(member.getUsername()).thenReturn("Zoë");
        when(member.getLook()).thenReturn("hd-180-1");
        when(member.getJoinDate()).thenReturn(123456789);

        ByteBuf bytes = payload(new GuildMemberUpdateComposer(guild, member), Outgoing.GuildMemberUpdateComposer);
        try {
            assertEquals(101, bytes.readInt());
            assertEquals(1, bytes.readInt());
            assertEquals(202, bytes.readInt());
            assertEquals("Zoë", readString(bytes));
            assertEquals("hd-180-1", readString(bytes));
            assertEquals("123456789", readString(bytes));
            assertFalse(bytes.isReadable());
        } finally {
            bytes.release();
        }

        ByteBuf refresh = payload(new GuildRefreshMembersListComposer(guild), Outgoing.GuildRefreshMembersListComposer);
        try {
            assertEquals(101, refresh.readInt());
            assertEquals(0, refresh.readInt());
            assertFalse(refresh.isReadable());
        } finally {
            refresh.release();
        }
    }

    @Test
    void breedingFailureCarriesExactlyOneReasonForEverySupportedCode() {
        for (int reason = PetBreedingStartFailedComposer.NO_NESTS;
                reason <= PetBreedingStartFailedComposer.TOO_TIRED;
                reason++) {
            ByteBuf bytes =
                    payload(new PetBreedingStartFailedComposer(reason), Outgoing.PetBreedingStartFailedComposer);
            try {
                assertEquals(reason, bytes.readInt());
                assertFalse(bytes.isReadable());
            } finally {
                bytes.release();
            }
        }
    }

    private static GameClient clientInRoom(Room room) {
        GameClient client = mock(GameClient.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(client.getUser()).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        return client;
    }

    private static void assertThickness(MessageComposer composer, int header, int first, int second) {
        ByteBuf bytes = payload(composer, header);
        try {
            assertEquals(true, bytes.readBoolean());
            assertEquals(first, bytes.readInt());
            assertEquals(second, bytes.readInt());
            assertFalse(bytes.isReadable());
        } finally {
            bytes.release();
        }
    }

    private static ByteBuf payload(MessageComposer composer, int header) {
        ByteBuf bytes = composer.compose().get();
        assertEquals(bytes.readableBytes() - Integer.BYTES, bytes.readInt());
        assertEquals(header, bytes.readUnsignedShort());
        return bytes;
    }

    private static String readString(ByteBuf bytes) {
        return bytes.readCharSequence(bytes.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
