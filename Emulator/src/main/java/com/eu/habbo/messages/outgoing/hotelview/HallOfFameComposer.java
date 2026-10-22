package com.eu.habbo.messages.outgoing.hotelview;

import com.eu.habbo.habbohotel.communitygoals.CommunityGoalHallOfFameEntry;
import com.eu.habbo.habbohotel.hotelview.HallOfFame;
import com.eu.habbo.habbohotel.hotelview.HallOfFameWinner;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AIR {@code CommunityGoalHallOfFame} (3005): {@code string goalCode}, then per player
 * {@code int userId, string userName, string figure, int rank, int currentScore}.
 *
 * <p>Built either from the legacy {@link HallOfFame} (the {@code hotelview.halloffame.query}
 * list sent with the news) or from the contributors of one community goal
 * ({@code GetCommunityGoalHallOfFame} 2167).
 */
public class HallOfFameComposer extends MessageComposer {
    private final HallOfFame hallOfFame;
    private final String goalCode;
    private final List<CommunityGoalHallOfFameEntry> entries;

    public HallOfFameComposer(HallOfFame hallOfFame) {
        this.hallOfFame = hallOfFame;
        this.goalCode = hallOfFame.getCompetitionName();
        this.entries = legacyEntries(hallOfFame);
    }

    public HallOfFameComposer(String goalCode, List<CommunityGoalHallOfFameEntry> entries) {
        this.hallOfFame = null;
        this.goalCode = goalCode == null ? "" : goalCode;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    private static List<CommunityGoalHallOfFameEntry> legacyEntries(HallOfFame hallOfFame) {
        List<HallOfFameWinner> winners = new ArrayList<>(hallOfFame.getWinners().values());
        Collections.sort(winners);

        List<CommunityGoalHallOfFameEntry> entries = new ArrayList<>(winners.size());
        int count = 1;

        for (HallOfFameWinner winner : winners) {
            entries.add(new CommunityGoalHallOfFameEntry(
                    winner.getId(), winner.getUsername(), winner.getLook(), count, winner.getPoints()));
            count++;
        }

        return entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HallOfFameComposer);
        this.response.appendString(this.goalCode);
        this.response.appendInt(this.entries.size());

        for (CommunityGoalHallOfFameEntry entry : this.entries) {
            this.response.appendInt(entry.userId());
            this.response.appendString(entry.userName());
            this.response.appendString(entry.figure());
            this.response.appendInt(entry.rank());
            this.response.appendInt(entry.currentScore());
        }
        return this.response;
    }

    public HallOfFame getHallOfFame() {
        return hallOfFame;
    }

    public String getGoalCode() {
        return goalCode;
    }

    public List<CommunityGoalHallOfFameEntry> getEntries() {
        return entries;
    }
}
