package com.eu.habbo.habbohotel.wired.highscores;

/**
 * The codes are read by the client, which picks the board's caption from a fixed list:
 * {@code ["perteam","mostwins","classic","fastesttime","longesttime"]}. Slot 3 is the fastest
 * time, not the longest - LONGESTTIME sat there and every conforming client labelled a
 * longest-time board "fastest time".
 */
public enum WiredHighscoreScoreType {
    PERTEAM(0),
    MOSTWIN(1),
    CLASSIC(2),
    FASTESTTIME(3),
    LONGESTTIME(4);

    public final int type;

    WiredHighscoreScoreType(int type) {
        this.type = type;
    }
}
