package polaris.messages.incoming.guides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guides.GuideTour;
import polaris.messages.ClientMessage;

/** A help request reaches the helper capped and filtered, like a guide message. */
class RequestGuideAssistanceEventTest {

    private static ClientMessage request(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var buffer = Unpooled.buffer();
        buffer.writeInt(0);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
        return new ClientMessage(0, buffer);
    }

    @Test
    void theRequestIsCappedAndFiltered() throws Exception {
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getGuideManager().getGuideTourByUser(any())).thenReturn(null);
        when(environment.getWordFilter().filter(anyString(), any()))
                .thenAnswer(call -> "[" + call.getArgument(0) + "]");

        RequestGuideAssistanceEvent event = new RequestGuideAssistanceEvent();
        event.client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        event.packet = request("x".repeat(400));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<AchievementManager> achievements = mockStatic(AchievementManager.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            event.handle();
        }

        ArgumentCaptor<GuideTour> tour = ArgumentCaptor.forClass(GuideTour.class);
        verify(environment.getGuideManager()).findHelper(tour.capture());
        assertEquals("[" + "x".repeat(255) + "]", tour.getValue().getHelpRequest());
    }

    @Test
    void theRequestIsRateLimited() {
        assertTrue(new RequestGuideAssistanceEvent().getRatelimit() > 0);
    }
}
