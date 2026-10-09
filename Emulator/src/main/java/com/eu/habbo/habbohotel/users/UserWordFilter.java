package com.eu.habbo.habbohotel.users;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A user's personal word filter: the words they chose to have masked in the chat they receive.
 *
 * <p>Pure in-memory state; {@link HabboStats} owns persistence. Words are matched case-insensitively
 * as plain substrings, the same way the room word filter masks them, and the replacement is the
 * hotel word-filter replacement ("bobba" unless configured otherwise).
 */
public final class UserWordFilter {
    /** The room word filter caps its words at 25 characters; the personal one follows it. */
    public static final int MAX_WORD_LENGTH = 25;
    /** Every received message is checked against each word, so the list is capped. */
    public static final int MAX_WORDS = 100;

    private final Set<String> words = new LinkedHashSet<>();

    /** Trims, lower-cases and caps a word; returns {@code null} when nothing usable is left. */
    public static String normalize(String word) {
        if (word == null) {
            return null;
        }
        String trimmed = word.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_WORD_LENGTH ? trimmed.substring(0, MAX_WORD_LENGTH) : trimmed;
    }

    public synchronized boolean add(String word) {
        String normalized = normalize(word);
        return normalized != null && this.words.size() < MAX_WORDS && this.words.add(normalized);
    }

    public synchronized boolean remove(String word) {
        String normalized = normalize(word);
        return normalized != null && this.words.remove(normalized);
    }

    public synchronized boolean contains(String word) {
        String normalized = normalize(word);
        return normalized != null && this.words.contains(normalized);
    }

    public synchronized boolean isEmpty() {
        return this.words.isEmpty();
    }

    public synchronized int size() {
        return this.words.size();
    }

    /** Insertion-ordered snapshot, safe to serialize while another thread edits the list. */
    public synchronized List<String> words() {
        return Collections.unmodifiableList(new ArrayList<>(this.words));
    }

    /** Masks every listed word in {@code message}; returns the input untouched when nothing matches. */
    public String apply(String message, String replacement) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        List<String> snapshot = this.words();
        if (snapshot.isEmpty()) {
            return message;
        }
        String result = message;
        for (String word : snapshot) {
            result = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(result)
                .replaceAll(Matcher.quoteReplacement(replacement));
        }
        return result;
    }
}
