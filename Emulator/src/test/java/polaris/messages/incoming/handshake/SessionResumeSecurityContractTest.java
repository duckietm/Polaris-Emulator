package polaris.messages.incoming.handshake;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SessionResumeSecurityContractTest {
    @Test
    void resumedSessionRechecksBansBeforeLoginContinues() throws Exception {
        String login =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/handshake/SecureLoginEvent.java"));

        int resume = login.indexOf("resumeSession(lookupUserId)");
        int securityCheck = login.indexOf("user.passesConnectionSecurityChecks()", resume);
        int loginResponse = login.indexOf("new SecureLoginOKComposer(", securityCheck);

        assertTrue(resume > -1);
        assertTrue(securityCheck > resume, "resumed Habbo must recheck account, IP, and machine bans");
        assertTrue(loginResponse > securityCheck, "ban checks must run before successful-login responses");
    }

    @Test
    void normalAndResumedConnectionsShareTheSameSecurityChecks() throws Exception {
        String user = Files.readString(Path.of("src/main/java/polaris/hotel/users/User.java"));

        int connect = user.indexOf("public boolean connect()");
        int sharedCheck = user.indexOf("this.checkConnectionSecurity()", connect);
        int accountBan = user.indexOf("checkForBan(this.userInfo.getId())", sharedCheck);
        int macBan = user.indexOf("hasMACBan(this.client)", accountBan);
        int ipBan = user.indexOf("hasIPBan(this.userInfo.getIpLogin())", macBan);

        assertTrue(sharedCheck > connect, "normal login must use the shared check");
        assertTrue(accountBan > sharedCheck, "shared check must revalidate account bans");
        assertTrue(macBan > accountBan, "shared check must revalidate machine bans");
        assertTrue(ipBan > macBan, "shared check must revalidate IP bans");
    }
}
