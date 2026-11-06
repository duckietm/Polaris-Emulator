package polaris.hotel.guilds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuildManagerMembershipContractTest {
    private static String guildManagerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/hotel/guilds/GuildManager.java"));
    }

    @Test
    void acceptRequestOnlyPromotesPendingMembershipRows() throws Exception {
        String source = guildManagerSource();

        assertTrue(
                source.contains(
                        "UPDATE guilds_members SET level_id = ?, member_since = ? WHERE user_id = ? AND guild_id = ? AND level_id = ?"),
                "accepting a guild request must only promote rows still in REQUESTED state");
        assertTrue(
                source.contains("statement.setInt(5, GuildRank.REQUESTED.type);"),
                "the accept-request update must bind the expected REQUESTED rank guard");
    }
}
