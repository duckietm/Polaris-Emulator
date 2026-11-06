package polaris.threading.runnables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.games.Game;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeam;

public class SaveScoreForTeam implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaveScoreForTeam.class);

    public final GameTeam team;
    public final Game game;

    public SaveScoreForTeam(GameTeam team, Game game) {
        this.team = team;
        this.game = game;
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_game_scores (room_id, game_start_timestamp, game_name, user_id, team_id, score, team_score) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (GamePlayer player : this.team.getMembers()) {
                statement.setInt(1, this.game.getRoom().getId());
                statement.setInt(2, this.game.getStartTime());
                statement.setString(3, this.game.getClass().getName());
                statement.setInt(4, player.getUser().getUserInfo().getId());
                statement.setInt(5, player.getTeamColor().type);
                statement.setInt(6, player.getScore());
                statement.setInt(7, this.team.getTeamScore());
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
