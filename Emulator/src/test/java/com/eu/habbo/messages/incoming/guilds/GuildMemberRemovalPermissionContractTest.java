package com.eu.habbo.messages.incoming.guilds;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildMemberRemovalPermissionContractTest {
    @Test
    void regularAdminsCannotRemovePeerAdmins() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/GuildRemoveMemberEvent.java"));

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
}
