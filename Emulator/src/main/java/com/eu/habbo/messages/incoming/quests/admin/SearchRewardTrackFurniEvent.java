package com.eu.habbo.messages.incoming.quests.admin;

import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;
import com.eu.habbo.messages.outgoing.quests.RewardTrackFurniSearchResultComposer;

/** Looks up furni by name for the prize form: the query, answered with up to thirty matches. */
public class SearchRewardTrackFurniEvent extends RewardTrackAdminEvent {
    private static final int QUERY_MAX_LENGTH = 64;

    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        String query = this.packet.readString().trim();
        if (query.length() > QUERY_MAX_LENGTH) {
            query = query.substring(0, QUERY_MAX_LENGTH);
        }
        this.client.sendResponse(new RewardTrackFurniSearchResultComposer(query, RewardTrackAdmin.searchFurni(query)));
    }
}
