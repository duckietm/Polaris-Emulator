package polaris.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.users.CitizenshipVipOfferPromoEnabledComposer;

/** AIR's wired menu refusal (289, one short) and the citizenship club promo trigger (2764, no body). */
class WiredMenuErrorWireTest {

    @Test
    void theErrorIsAirsHeaderAndOneShort() {
        ByteBuffer body = body(
                new WiredMenuErrorComposer(WiredMenuErrorComposer.NOT_VARIABLE_OWNER), Outgoing.WiredMenuErrorComposer);

        assertEquals(289, Outgoing.WiredMenuErrorComposer);
        assertEquals(7, body.getShort());
        assertEquals(0, body.remaining());
    }

    @Test
    void thePromoIsAirsHeaderWithoutBody() {
        ByteBuffer body =
                body(new CitizenshipVipOfferPromoEnabledComposer(), Outgoing.CitizenshipVipOfferPromoEnabledComposer);

        assertEquals(2764, Outgoing.CitizenshipVipOfferPromoEnabledComposer);
        assertEquals(0, body.remaining());
    }

    @Test
    void theOwnerOnlyRefusalsAnswerWithTheError() throws IOException {
        String manage = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/wired/WiredUserVariableManageEvent.java"));
        String update = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/wired/WiredUserVariableUpdateEvent.java"));
        String refusal = "new WiredMenuErrorComposer(WiredMenuErrorComposer.NOT_VARIABLE_OWNER)";

        // Clearing from everyone and removing from an absent holder, then changing an absent holder's value.
        assertEquals(2, manage.split(java.util.regex.Pattern.quote(refusal), -1).length - 1);
        assertTrue(update.contains(refusal));
    }

    @Test
    void freeClubFromCitizenshipAnnouncesThePromo() throws IOException {
        String manager = Files.readString(Path.of("src/main/java/polaris/hotel/achievements/AchievementManager.java"));

        assertTrue(manager.contains("type == TalentTrackType.CITIZENSHIP && result.clubSeconds() > 0"));
        assertTrue(manager.contains("new CitizenshipVipOfferPromoEnabledComposer()"));
    }

    private static ByteBuffer body(MessageComposer composer, int header) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(bytes.length - 4, buffer.getInt());
            assertEquals(header, buffer.getShort());
            return buffer.slice();
        } finally {
            packet.release();
        }
    }
}
