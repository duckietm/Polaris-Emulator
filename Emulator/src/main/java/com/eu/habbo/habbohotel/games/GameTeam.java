package com.eu.habbo.habbohotel.games;

import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GameTeam {

    public final GameTeamColors teamColor;
    // Concurrent: the game cycle iterates the members while a player leaving removes them from a
    // packet thread, which threw ConcurrentModificationException and left the game stuck.
    private final Set<GamePlayer> members;
    private final AtomicInteger teamScore = new AtomicInteger();

    public GameTeam(GameTeamColors teamColor) {
        this.teamColor = teamColor;

        this.members = ConcurrentHashMap.newKeySet();
    }

    public void initialise() {
        for (GamePlayer player : this.members) {
            player.reset();
        }

        this.teamScore.set(0);
    }

    public void reset() {
        this.members.clear();
    }

    public void addTeamScore(int teamScore) {
        this.teamScore.addAndGet(teamScore);
    }

    public int getTeamScore() {
        return this.teamScore.get();
    }

    public synchronized int getTotalScore() {
        int score = this.teamScore.get();

        for (GamePlayer player : this.members) {
            score += player.getScore();
        }

        return score;
    }

    public void addMember(GamePlayer gamePlayer) {
        synchronized (this.members) {
            this.members.add(gamePlayer);
        }
    }

    public void removeMember(GamePlayer gamePlayer) {
        synchronized (this.members) {
            this.members.remove(gamePlayer);
        }
    }

    public void clearMembers() {
        for (GamePlayer player : this.members) {
            if (player == null || player.getHabbo() == null) continue;

            if (player.getHabbo().getHabboInfo().getGamePlayer() != null)
                player.getHabbo().getHabboInfo().getGamePlayer().reset();
            player.getHabbo().getHabboInfo().setCurrentGame(null);
            player.getHabbo().getHabboInfo().setGamePlayer(null);
        }

        this.members.clear();
    }

    public void resetScores() {
        for (GamePlayer player : this.members) {
            if (player == null) continue;

            player.reset();
        }

        this.teamScore.set(0);
    }

    public Set<GamePlayer> getMembers() {
        return this.members;
    }

    public boolean isMember(Habbo habbo) {
        for (GamePlayer p : this.members) {
            if (p.getHabbo().equals(habbo)) {
                return true;
            }
        }

        return false;
    }

    @Deprecated
    public GamePlayer getPlayerForHabbo(Habbo habbo) {
        for (GamePlayer p : this.members) {
            if (p.getHabbo().equals(habbo)) {
                return p;
            }
        }

        return null;
    }
}
