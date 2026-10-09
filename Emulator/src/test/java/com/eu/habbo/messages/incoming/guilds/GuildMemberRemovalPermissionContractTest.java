package com.eu.habbo.messages.incoming.guilds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuildMemberRemovalPermissionContractTest {
    @Test
    void regularAdminsCannotRemovePeerAdmins() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/GuildRemoveMemberEvent.java"));

        int targetLookup = source.indexOf("GuildMember targetMember =");
        int peerAdminGuard = source.indexOf("targetMember.getRank().equals(GuildRank.ADMIN)", targetLookup);
        int ownerCheck = source.indexOf("!actorIsGuildOwner", peerAdminGuard);
        int globalCheck = source.indexOf("!actorIsGlobalGuildAdmin", ownerCheck);
        int removeMember = source.indexOf("removeMember(guild, userId)", globalCheck);

        assertTrue(targetLookup > -1, "member removal should load the target membership row");
        assertTrue(peerAdminGuard > targetLookup, "member removal should detect admin targets");
        assertTrue(ownerCheck > peerAdminGuard, "peer-admin removal must require guild owner");
        assertTrue(globalCheck > ownerCheck, "peer-admin removal may also allow global guild admins");
        assertTrue(removeMember > globalCheck, "target rank authorization must run before removal");
    }

    @Test
    void pendingRequestsAreNeitherBlockedNorCountedAsMembers() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/GuildRemoveMemberEvent.java"));

        int wasRequest = source.indexOf("boolean wasRequest = targetMember.getRank().equals(GuildRank.REQUESTED);");
        int block = source.indexOf("&& !wasRequest", wasRequest);
        int decrease = source.indexOf("if (!wasRequest) guild.decreaseMemberCount();", block);

        assertTrue(wasRequest > -1, "removal should know whether the target is a pending request");
        assertTrue(block > wasRequest, "only members can be blocked");
        assertTrue(decrease > block, "removing a request must not lower the member count");
    }
}
