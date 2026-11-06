package polaris.hotel.games.football;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.games.Game;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserAction;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserActionComposer;

public class FootballGame extends Game {
    private Room room;

    public FootballGame(Room room) {
        super(null, null, room, true);

        this.room = room;
    }

    @Override
    public void initialise() {}

    @Override
    public void run() {}

    public void onScore(RoomUnit kicker, GameTeamColors team) {
        if (this.room == null || !this.room.isLoaded()) return;

        User user = this.room.getUser(kicker);
        if (user != null) {
            AchievementManager.progressAchievement(
                    user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("FootballGoalScored"));
            if (user.getUserInfo().getId() != this.room.getOwnerId()) {
                AchievementManager.progressAchievement(
                        this.room.getOwnerId(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("FootballGoalScoredInRoom"));
            }
        }

        this.room.sendComposer(new RoomUserActionComposer(kicker, RoomUserAction.WAVE).compose());

        for (Map.Entry<Integer, InteractionFootballScoreboard> scoreBoard :
                this.room.getRoomSpecialTypes().getFootballScoreboards(team).entrySet()) {
            scoreBoard.getValue().changeScore(1);
        }
    }
}
