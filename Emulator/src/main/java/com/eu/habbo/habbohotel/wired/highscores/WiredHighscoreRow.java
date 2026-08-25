package com.eu.habbo.habbohotel.wired.highscores;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WiredHighscoreRow implements Comparable<WiredHighscoreRow> {
    public static final Comparator<WiredHighscoreRow> COMPARATOR = Comparator.comparing(WiredHighscoreRow::getValue).reversed();

    private final List<String> users;
    private final int value;

    public WiredHighscoreRow(List<String> users, int value) {
        Collections.sort(users);

        this.users = users;
        this.value = value;
    }

    public List<String> getUsers() {
        return users;
    }

    public int getValue() {
        return value;
    }

    @Override
    public int compareTo(WiredHighscoreRow otherRow) {
        return COMPARATOR.compare(this, otherRow);
    }
}
