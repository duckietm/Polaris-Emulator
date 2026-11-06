package polaris.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.ClientMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;

/**
 * What a Builders Club placement packet may carry. The refusal comes before anything about the room is read, so a
 * client cannot reach the placement code with a rotation or a wall position the normal placement never accepts.
 */
class BuildersClubPlacementInputTest {
    @Test
    void aFloorFurniWithARotationOutsideTheEightDirectionsIsRefused() throws Exception {
        for (int rotation : new int[] {-1, 8, 99, Integer.MIN_VALUE}) {
            GameClient client = mock(GameClient.class);
            ClientMessage packet = mock(ClientMessage.class);

            // page, offer, x, y, rotation
            when(packet.readInt()).thenReturn(1, 2, 3, 4, rotation);
            when(packet.readString()).thenReturn("");
            when(packet.readBoolean()).thenReturn(false);

            BuildersClubPlaceRoomItemEvent event = new BuildersClubPlaceRoomItemEvent();
            event.client = client;
            event.packet = packet;

            event.handle();

            ArgumentCaptor<MessageComposer> answer = ArgumentCaptor.forClass(MessageComposer.class);
            verify(client).sendResponse(answer.capture());
            assertInstanceOf(BubbleAlertComposer.class, answer.getValue());
            verify(client, never()).getUser();
        }
    }

    @Test
    void aWallFurniWithAMalformedPositionIsRefused() throws Exception {
        for (String position :
                new String[] {"", "garbage", ":w=1,2 l=3,4 x", ":w=1,2 l=3,4 l" + "x".repeat(200), null}) {
            GameClient client = mock(GameClient.class);
            ClientMessage packet = mock(ClientMessage.class);

            when(packet.readInt()).thenReturn(1, 2);
            when(packet.readString()).thenReturn("", position);
            when(packet.readBoolean()).thenReturn(false);

            BuildersClubPlaceWallItemEvent event = new BuildersClubPlaceWallItemEvent();
            event.client = client;
            event.packet = packet;

            event.handle();

            ArgumentCaptor<MessageComposer> answer = ArgumentCaptor.forClass(MessageComposer.class);
            verify(client).sendResponse(answer.capture());
            assertInstanceOf(BubbleAlertComposer.class, answer.getValue());
            verify(client, never()).getUser();
        }
    }
}
