package polaris.messages.incoming.ambassadors;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Ambassadors followed users who hide or block following, and alerted users above their rank. */
class AmbassadorTargetContractTest {

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/incoming/ambassadors/" + name));
    }

    @Test
    void aVisitRespectsPrivacyAndRank() throws Exception {
        String visit = source("AmbassadorVisitCommandEvent.java");

        int privacy = visit.indexOf("!user.getUserStats().blockFollowing");
        int forward = visit.indexOf("new ForwardToRoomComposer(");
        assertTrue(privacy > 0 && privacy < forward);
        assertTrue(visit.contains("user.getUserStats().hideOnline"));
        assertTrue(visit.indexOf("CommandTargetGuard.canTarget(") < forward);
    }

    @Test
    void anAlertRespectsRank() throws Exception {
        String alert = source("AmbassadorAlertCommandEvent.java");

        int guard = alert.indexOf("CommandTargetGuard.canTarget(this.client.getUser(), user)");
        assertTrue(guard > 0 && guard < alert.indexOf("new SupportUserAlertedEvent("));
    }
}
