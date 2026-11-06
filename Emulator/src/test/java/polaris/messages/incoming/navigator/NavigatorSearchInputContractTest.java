package polaris.messages.incoming.navigator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NavigatorSearchInputContractTest {
    @Test
    void classicSearchNormalizesInputAndPassesUnprefixedQueriesToManagers() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/navigator/SearchRoomsEvent.java"));

        assertTrue(
                source.contains("NavigatorInputGuard.normalizeSearch(this.packet.readString())"),
                "classic room search must normalize raw client text before cache or manager lookups");
        assertTrue(source.contains("getRoomsForUser(query)"), "owner search must pass only the unprefixed owner query");
        assertTrue(source.contains("getRoomsWithTag(query)"), "tag search must pass only the unprefixed tag query");
        assertTrue(
                source.contains("getGroupRoomsWithName(query)"),
                "group search must pass only the unprefixed group query");
        assertTrue(
                source.contains("buildCacheKey(prefix, query)"),
                "classic room search must cache using normalized prefix/query pairs");
    }

    @Test
    void savedAndTagSearchesNormalizeText() throws Exception {
        String saved =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/navigator/AddSavedSearchEvent.java"));
        String tag = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/navigator/SearchRoomsByTagEvent.java"));

        assertTrue(
                saved.contains("NavigatorInputGuard.normalizeSavedSearchValue"),
                "saved searches must trim and bound search code/filter values");
        assertTrue(tag.contains("NavigatorInputGuard.normalizeSearch"), "tag search must trim and bound tag values");
    }
}
