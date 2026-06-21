package com.eu.habbo.habbohotel.hotelview;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class HallOfFame {
    private static final Logger LOGGER = LoggerFactory.getLogger(HallOfFame.class);

    private final Map<Integer, HallOfFameWinner> winners = new HashMap<>();


    private String competitionName;

    public HallOfFame() {
        this.setCompetitionName("xmasRoomComp");

        this.reload();
    }


    public void reload() {
        this.winners.clear();

        synchronized (this.winners) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery(Emulator.getConfig().getValue("hotelview.halloffame.query"))) {
                while (set.next()) {
                    HallOfFameWinner winner = new HallOfFameWinner(set);
                    this.winners.put(winner.getId(), winner);
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public Map<Integer, HallOfFameWinner> getWinners() {
        return this.winners;
    }

    public String getCompetitionName() {
        return this.competitionName;
    }

    void setCompetitionName(String name) {
        this.competitionName = name;
    }
}
