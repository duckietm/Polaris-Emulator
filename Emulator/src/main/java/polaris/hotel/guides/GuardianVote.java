package polaris.hotel.guides;

import polaris.hotel.users.User;

public class GuardianVote implements Comparable<GuardianVote> {
    public final int id;
    final User guardian;
    public GuardianVoteType type;
    boolean ignore;

    public GuardianVote(int id, User guardian) {
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
        if (o instanceof GuardianVote) {
            return ((GuardianVote) o).id == this.id
                    && ((GuardianVote) o).guardian == this.guardian
                    && ((GuardianVote) o).type == this.type;
        }

        return false;
    }

    public User getGuardian() {
        return this.guardian;
    }

    public void ignore() {
        this.ignore = true;
    }
}
