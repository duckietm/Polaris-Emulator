package com.eu.habbo.messages.incoming.rooms.competition;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomCompetitionPacketContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    private static String handler(String name) throws Exception {
        return source("com/eu/habbo/messages/incoming/rooms/competition/" + name + ".java");
    }

    @Test
    void everyHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        for (String pair : new String[] {
            "VoteForRoomEvent = 143",
            "ForwardToACompetitionRoomEvent = 172",
            "ForwardToRandomCompetitionRoomEvent = 865",
            "RoomCompetitionInitEvent = 1334",
            "ForwardToASubmittableRoomEvent = 1450",
            "GetIsUserPartOfCompetitionEvent = 2077",
            "SubmitRoomToCompetitionEvent = 2595",
            "CompetitionRoomsSearchEvent = 433"
        }) {
            assertTrue(incoming.contains(pair), pair);
            String name = pair.substring(0, pair.indexOf(' '));
            assertTrue(registry.contains("Incoming." + name + ", " + name + ".class"), name);
        }
    }

    @Test
    void theOwnerSeesTheRulesBeforeTheSubmitButton() throws Exception {
        String init = handler("RoomCompetitionInitEvent");

        assertTrue(init.contains("room.getOwnerId() == habbo.getHabboInfo().getId()"));
        assertTrue(init.contains("RoomCompetitionResult.RULES"));
        assertTrue(init.contains("competition.submissionOpen(now)"));
        assertTrue(init.contains("competition.votingOpen(now)"));
    }

    @Test
    void theParticipantsTravelInAnOrdinaryNavigatorSearchResult() throws Exception {
        String handler = handler("CompetitionRoomsSearchEvent");

        assertTrue(handler.contains("new NewNavigatorSearchResultsComposer(SEARCH_CODE, competition.code(), results)"));
        assertTrue(handler.contains("new CompetitionRoomsDataComposer(competition.id(), pageIndex, pages)"));
        assertTrue(handler.contains("entryRoomsPage(competition, pageIndex, PAGE_SIZE)"));
    }

    @Test
    void aRoomTooOldForTheCompetitionIsNotEligible() throws Exception {
        String manager = source("com/eu/habbo/habbohotel/rooms/competition/RoomCompetitionManager.java");

        assertTrue(manager.contains("competition.acceptsRoomCreatedAt(this.roomCreatedAt(room.getId()))"));
        assertTrue(manager.contains("SELECT date_created FROM rooms WHERE id = ? LIMIT 1"));
    }

    @Test
    void onlyTheLastStepWritesAnEntryAndEveryStepRereadsTheRoom() throws Exception {
        String submit = handler("SubmitRoomToCompetitionEvent");

        assertTrue(submit.contains("manager.submitState(habbo, room, competition)"));
        assertTrue(submit.contains("state.result() != RoomCompetitionResult.READY || level == LEVEL_REFRESH"));
        assertTrue(submit.contains("level == LEVEL_CONFIRM"));
        assertTrue(submit.contains("manager.submit(habbo, room, competition)"));
        assertTrue(submit.indexOf("manager.submit(habbo, room, competition)") > submit.indexOf("LEVEL_CONFIRM"));
    }

    @Test
    void votingRefusesYourOwnRoomAndARoomThatNeverEntered() throws Exception {
        String vote = handler("VoteForRoomEvent");
        String support = handler("RoomCompetitionSupport");

        assertTrue(support.contains("room.getOwnerId() == habbo.getHabboInfo().getId()"));
        assertTrue(vote.contains("RoomCompetitionSupport.votableEntryId(habbo, room, competition)"));
        assertTrue(vote.contains("if (entryId <= 0) return;"));
        assertTrue(vote.contains("votesLeft > 0"));
    }

    @Test
    void theForwardsOnlyEverPointAtARoomTakingPart() throws Exception {
        assertTrue(handler("ForwardToACompetitionRoomEvent")
                .contains("RoomCompetitionManager.getInstance().entryId(roomId, competition) <= 0"));
        assertTrue(handler("ForwardToRandomCompetitionRoomEvent").contains("randomEntryRoom(competition,"));
        assertTrue(handler("ForwardToASubmittableRoomEvent").contains("new OpenRoomCreationWindowComposer()"));
    }
}
