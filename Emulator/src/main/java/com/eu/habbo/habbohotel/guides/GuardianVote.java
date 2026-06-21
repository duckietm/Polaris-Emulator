package com.eu.habbo.habbohotel.guides;

import com.eu.habbo.habbohotel.users.Habbo;

public class GuardianVote implements Comparable<GuardianVote> {
    public final int id;
    final Habbo guardian;
    public GuardianVoteType type;
    boolean ignore;

    public GuardianVote(int id, Habbo guardian) {
        this.id = id;
        this.guardian = guardian;
        this.type = GuardianVoteType.SEARCHING;
        this.ignore = false;
    }

    @Override
    public int compareTo(GuardianVote o) {
        return this.id - o.id;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GuardianVote vote) {
            return vote.id == this.id && vote.guardian == this.guardian && vote.type == this.type;
        }

        return false;
    }

    public void ignore() {
        this.ignore = true;
    }
}
