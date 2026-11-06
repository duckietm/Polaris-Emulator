package polaris.hotel.mentions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MentionTokensTest {
    private static final Set<String> EVERYONE = Set.of("all", "everyone", "tutti");
    private static final Set<String> FRIENDS = Set.of("friends", "amici");
    private static final Set<String> ROOM = Set.of("room", "stanza");

    private static MentionTokens.Parsed parse(String message) {
        return MentionTokens.parse(message, EVERYONE, FRIENDS, ROOM);
    }

    @Test
    void namesAreReadOneByOneInTheOrderTheyAppear() {
        MentionTokens.Parsed parsed = parse("hi @bob and @alice!");

        assertEquals(MentionManager.BroadcastScope.NONE, parsed.scope());
        assertEquals(List.of("bob", "alice!"), List.copyOf(parsed.directTokens()));
    }

    @Test
    void aliasesAreMatchedWithoutCaseOrTrailingPunctuation() {
        assertEquals(
                MentionManager.BroadcastScope.ROOM, parse("@Stanza, listen").scope());
        assertEquals(MentionManager.BroadcastScope.FRIENDS, parse("@AMICI!").scope());
        assertEquals(MentionManager.BroadcastScope.EVERYONE, parse("@tutti").scope());
    }

    @Test
    void theWidestBroadcastWinsAndAliasesAreNotNames() {
        MentionTokens.Parsed parsed = parse("@room @all @bob");

        assertEquals(MentionManager.BroadcastScope.EVERYONE, parsed.scope());
        assertEquals(List.of("bob"), List.copyOf(parsed.directTokens()));
    }

    @Test
    void aLoneAtSignAndPlainWordsMentionNobody() {
        MentionTokens.Parsed parsed = parse("mail me @ home, not@here");

        assertEquals(MentionManager.BroadcastScope.NONE, parsed.scope());
        assertTrue(parsed.directTokens().isEmpty());
    }

    @Test
    void trailingPunctuationIsTrimmedOnlyAtTheEnd() {
        assertEquals("bob", MentionTokens.trimTrailingPunctuation("bob?!"));
        assertEquals("a.b", MentionTokens.trimTrailingPunctuation("a.b."));
        assertEquals("", MentionTokens.trimTrailingPunctuation("..."));
    }
}
