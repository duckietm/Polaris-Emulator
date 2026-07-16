package com.eu.habbo.messages.incoming.handshake;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionResumeSecurityContractTest {
    @Test
    void resumedSessionRechecksBansBeforeLoginContinues() throws Exception {
        String login = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));

        int resume = login.indexOf("resumeSession(lookupUserId)");
        int securityCheck = login.indexOf("habbo.passesConnectionSecurityChecks()", resume);
        int loginResponse = login.indexOf("new SecureLoginOKComposer()", securityCheck);

        assertTrue(resume > -1);
        assertTrue(securityCheck > resume, "resumed Habbo must recheck account, IP, and machine bans");
        assertTrue(loginResponse > securityCheck, "ban checks must run before successful-login responses");
    }

    @Test
    void normalAndResumedConnectionsShareTheSameSecurityChecks() throws Exception {
        String habbo = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/users/Habbo.java"));

        int connect = habbo.indexOf("public boolean connect()");
        int sharedCheck = habbo.indexOf("this.checkConnectionSecurity()", connect);
        int accountBan = habbo.indexOf("checkForBan(this.habboInfo.getId())", sharedCheck);
        int macBan = habbo.indexOf("hasMACBan(this.client)", accountBan);
        int ipBan = habbo.indexOf("hasIPBan(this.habboInfo.getIpLogin())", macBan);

        assertTrue(sharedCheck > connect, "normal login must use the shared check");
        assertTrue(accountBan > sharedCheck, "shared check must revalidate account bans");
        assertTrue(macBan > accountBan, "shared check must revalidate machine bans");
        assertTrue(ipBan > macBan, "shared check must revalidate IP bans");
    }
}
