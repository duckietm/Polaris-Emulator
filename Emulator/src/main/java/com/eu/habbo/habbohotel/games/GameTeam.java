package com.eu.habbo.habbohotel.games;

import com.eu.habbo.habbohotel.users.Habbo;

import java.util.HashSet;
import java.util.Set;

public class GameTeam {

    public final GameTeamColors teamColor;
    private final Set<GamePlayer> members;
    private int teamScore;


    public GameTeam(GameTeamColors teamColor) {
        this.teamColor = teamColor;

        this.members = new HashSet<>();
    }


    public void initialise() {
        for (GamePlayer player : this.members) {
            player.reset();
        }

        this.teamScore = 0;
    }


    public void reset() {
        this.members.clear();
    }


    public void addTeamScore(int teamScore) {
        this.teamScore += teamScore;
    }


    public int getTeamScore() {
        return this.teamScore;
    }


    public synchronized int getTotalScore() {
        int score = this.teamScore;

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

            if (player.getHabbo().getHabboInfo().getGamePlayer() != null) player.getHabbo().getHabboInfo().getGamePlayer().reset();
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

        this.teamScore = 0;
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
