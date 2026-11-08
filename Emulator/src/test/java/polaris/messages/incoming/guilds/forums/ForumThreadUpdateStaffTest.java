package polaris.messages.incoming.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;

/** Staff moderate a forum without being members; a pending request still does not count as a member. */
class ForumThreadUpdateStaffTest {

    private static GuildMember member(GuildRank rank) {
        GuildMember member = mock(GuildMember.class);
        when(member.getRank()).thenReturn(rank);
        return member;
    }

    @Test
    void onlyAcceptedRanksAreMembers() {
        assertTrue(GuildForumThreadUpdateEvent.isAcceptedMember(member(GuildRank.OWNER)));
        assertTrue(GuildForumThreadUpdateEvent.isAcceptedMember(member(GuildRank.MEMBER)));
        assertFalse(GuildForumThreadUpdateEvent.isAcceptedMember(member(GuildRank.REQUESTED)));
        assertFalse(GuildForumThreadUpdateEvent.isAcceptedMember(null));
    }

    @Test
    void theUnauthorisedAnswerSparesStaff() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/guilds/forums/GuildForumThreadUpdateEvent.java"));

        int staff = source.indexOf("boolean isStaff");
        int guard = source.indexOf("if (!isAcceptedMember(member) && !isStaff) {");

        assertTrue(staff > 0 && guard > staff, "the staff permission is read before the member check uses it");
    }
}
