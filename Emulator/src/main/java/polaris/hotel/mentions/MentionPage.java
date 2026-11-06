package polaris.hotel.mentions;

import java.util.List;

/** One page of a user's mentions, newest first, and whether older ones remain. */
public record MentionPage(List<UserMention> mentions, boolean hasMore) {}
