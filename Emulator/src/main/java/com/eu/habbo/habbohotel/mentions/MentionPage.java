package com.eu.habbo.habbohotel.mentions;

import java.util.List;

/** One page of a user's mentions, newest first, and whether older ones remain. */
public record MentionPage(List<HabboMention> mentions, boolean hasMore) {}
