package polaris.hotel.mentions;

import java.util.LinkedHashSet;
import java.util.Set;

/** Reads the {@code @tokens} of a chat line; pure, so it can be tested without a running hotel. */
final class MentionTokens {

    /** The widest broadcast a line asks for and the names it mentions one by one. */
    record Parsed(MentionManager.BroadcastScope scope, LinkedHashSet<String> directTokens) {}

    private static final String TRAILING_PUNCTUATION = ".,!?;:)]}\"'";

    private MentionTokens() {}

    static Parsed parse(String message, Set<String> everyone, Set<String> friends, Set<String> room) {
        MentionManager.BroadcastScope scope = MentionManager.BroadcastScope.NONE;
        LinkedHashSet<String> directTokens = new LinkedHashSet<>();

        for (String token : message.split("\s+")) {
            if (token.length() < 2 || token.charAt(0) != '@') {
                continue;
            }

            String raw = token.substring(1);
            MentionManager.BroadcastScope tokenScope =
                    classify(trimTrailingPunctuation(raw).toLowerCase(), everyone, friends, room);

            if (tokenScope != MentionManager.BroadcastScope.NONE) {
                if (tokenScope.ordinal() > scope.ordinal()) {
                    scope = tokenScope;
                }
            } else if (!raw.isEmpty()) {
                directTokens.add(raw);
            }
        }

        return new Parsed(scope, directTokens);
    }

    static MentionManager.BroadcastScope classify(
            String alias, Set<String> everyone, Set<String> friends, Set<String> room) {
        if (alias.isEmpty()) return MentionManager.BroadcastScope.NONE;
        if (everyone.contains(alias)) return MentionManager.BroadcastScope.EVERYONE;
        if (friends.contains(alias)) return MentionManager.BroadcastScope.FRIENDS;
        if (room.contains(alias)) return MentionManager.BroadcastScope.ROOM;
        return MentionManager.BroadcastScope.NONE;
    }

    static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
