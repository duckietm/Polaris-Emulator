package com.eu.habbo.messages.incoming.guilds;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildMembersInputGuardContractTest {
    private static String eventSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/RequestGuildMembersEvent.java"));
    }

    private static String managerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/guilds/GuildManager.java"));
    }

    @Test
    void guildMemberListInputsAreBoundedBeforeManagerQueries() throws Exception {
        String source = eventSource();

        int pageRead = source.indexOf("int pageId = this.packet.readInt()");
        int queryRead = source.indexOf("String query = this.packet.readString()", pageRead);
        int levelRead = source.indexOf("int levelId = this.packet.readInt()", queryRead);
        int guard = source.indexOf("pageId < 0 || pageId > MAX_PAGE_ID", levelRead);
        int managerCall = source.indexOf("getGuildMembers(g, pageId, levelId, query)", guard);

        assertTrue(source.contains("MAX_PAGE_ID = 1000"),
                "Guild member pagination should have a server-side upper bound");
        assertTrue(source.contains("MAX_QUERY_LENGTH = 32"),
                "Guild member search query should have a server-side length bound");
        assertTrue(source.contains("MAX_LEVEL_ID = 2"),
                "Guild member rank filter should be bounded to known levels");
        assertTrue(pageRead > -1 && queryRead > pageRead && levelRead > queryRead,
                "Guild member handler must read page/query/level from the packet");
        assertTrue(guard > levelRead && guard < managerCall,
                "Guild member handler must validate inputs before querying the manager");
    }

    @Test
    void guildMemberCountEscapesLikeQuerySameAsListQuery() throws Exception {
        String source = managerSource();

        int listMethod = source.indexOf("public ArrayList<GuildMember> getGuildMembers(Guild guild, int page, int levelId, String query)");
        int listEscape = source.indexOf("SqlLikeEscaper.escape(query)", listMethod);
        int countMethod = source.indexOf("public int getGuildMembersCount(Guild guild, int page, int levelId, String query)");
        int countEscape = source.indexOf("SqlLikeEscaper.escape(query)", countMethod);

        assertTrue(listEscape > listMethod,
                "Guild member list query should escape SQL LIKE wildcards");
        assertTrue(countEscape > countMethod,
                "Guild member count query should escape SQL LIKE wildcards too");
    }
}
