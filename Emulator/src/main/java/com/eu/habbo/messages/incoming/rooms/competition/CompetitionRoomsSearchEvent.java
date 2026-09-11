package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.DisplayMode;
import com.eu.habbo.habbohotel.navigation.DisplayOrder;
import com.eu.habbo.habbohotel.navigation.ListMode;
import com.eu.habbo.habbohotel.navigation.SearchAction;
import com.eu.habbo.habbohotel.navigation.SearchResultList;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSearchResultsComposer;
import com.eu.habbo.messages.outgoing.rooms.competition.CompetitionRoomsDataComposer;
import java.util.ArrayList;
import java.util.List;

/**
 * The rooms taking part in the competition, most voted first, one page at a time. The rooms travel
 * in the ordinary navigator search result, so the client renders them like any other list, and the
 * page number travels beside it.
 */
public class CompetitionRoomsSearchEvent extends MessageHandler {
    static final String SEARCH_CODE = "competition";

    static final int PAGE_SIZE = 20;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int goalId = this.packet.readInt();
        int pageIndex = Math.max(0, this.packet.readInt());

        RoomCompetition competition = RoomCompetitionSupport.running(null);

        if (competition == null || (goalId > 0 && competition.id() != goalId)) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        List<Room> rooms = new ArrayList<>();

        for (int roomId : manager.entryRoomsPage(competition, pageIndex, PAGE_SIZE)) {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);

            if (room != null) rooms.add(room);
        }

        int total = manager.entryCount(competition);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);

        List<SearchResultList> results = new ArrayList<>();
        results.add(new SearchResultList(
                0,
                SEARCH_CODE,
                competition.code(),
                SearchAction.NONE,
                ListMode.LIST,
                DisplayMode.VISIBLE,
                rooms,
                false,
                false,
                DisplayOrder.ACTIVITY,
                -1));

        this.client.sendResponse(new NewNavigatorSearchResultsComposer(SEARCH_CODE, competition.code(), results));
        this.client.sendResponse(new CompetitionRoomsDataComposer(competition.id(), pageIndex, pages));
    }
}
