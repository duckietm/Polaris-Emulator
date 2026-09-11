package com.eu.habbo.habbohotel.rooms.competition;

import java.util.List;

/**
 * What the submit window should say about a room: the result code, the furniture the competition
 * asks for and, of that, what the room still lacks.
 */
public record RoomCompetitionSubmitState(int result, List<String> requiredFurni, List<String> missingFurni) {

    public static RoomCompetitionSubmitState of(int result) {
        return new RoomCompetitionSubmitState(result, List.of(), List.of());
    }
}
