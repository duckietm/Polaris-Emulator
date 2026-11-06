package polaris.hotel.games;

import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;

public class GamePlayer {

    private final User user;
    private GameTeamColors teamColor;
    private int score;
    private int wiredScore;

    public GamePlayer(User user, GameTeamColors teamColor) {
        this.user = user;
        this.teamColor = teamColor;
    }

    public void reset() {
        this.score = 0;
        this.wiredScore = 0;
    }

    public void addScore(int amount) {
        addScore(amount, false);
    }

    public void addScore(int amount, boolean isWired) {
        polaris.hotel.rooms.Room roomToTrigger = null;
        polaris.hotel.rooms.RoomUnit roomUnitToTrigger = null;
        int currentScore = 0;
        int scoreAdded = 0;
        GameTeam team = null;

        synchronized (this) {
            if (this.user.getUserInfo().getGamePlayer() != null
                    && this.user.getUserInfo().getCurrentGame() != null
                    && this.user
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getGame(this.user.getUserInfo().getCurrentGame())
                                    .getTeamForUser(this.user)
                            != null) {
                team = this.user
                        .getUserInfo()
                        .getCurrentRoom()
                        .getGame(this.user.getUserInfo().getCurrentGame())
                        .getTeamForUser(this.user);
                int scoreBefore = this.score;
                this.score += amount;

                if (this.score < 0) this.score = 0;

                if (isWired) {
                    this.wiredScore += amount;

                    if (this.wiredScore < 0) {
                        this.wiredScore = 0;
                    }

                    if (this.wiredScore > this.score) {
                        this.wiredScore = this.score;
                    }
                }

                roomToTrigger = this.user.getUserInfo().getCurrentRoom();
                roomUnitToTrigger = this.user.getRoomUnit();
                // What actually changed: a score cannot go below 0, so a removal may take less.
                scoreAdded = this.score - scoreBefore;
                currentScore = this.score;
            }
        }

        if (roomToTrigger != null && roomUnitToTrigger != null) {
            // "Score achieved" is about the team, as in Habbo: its total crossing the target, not one
            // player's own score. A player outside any team counts on their own.
            int teamScore = (team != null) ? team.getTotalScore() : currentScore;
            WiredManager.triggerScoreAchieved(roomToTrigger, roomUnitToTrigger, teamScore, scoreAdded);
        }
    }

    public User getUser() {
        return this.user;
    }

    public GameTeamColors getTeamColor() {
        return this.teamColor;
    }

    public int getScore() {
        return this.score;
    }

    public int getScoreAchievementValue() {
        return this.score - this.wiredScore;
    }
}
