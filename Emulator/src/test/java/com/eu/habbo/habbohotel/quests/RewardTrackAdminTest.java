package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The staff editor's input rules, without a database. */
class RewardTrackAdminTest {

    private static RewardTrackAdmin.TrackInput track(String id, int startsAt, int endsAt) {
        return new RewardTrackAdmin.TrackInput(id, "blue", 0, startsAt, endsAt, true, 150, 50, 25, 0, true);
    }

    private static RewardTrackAdmin.TaskInput task(String actionType, List<RewardTrack.Level> levels) {
        return new RewardTrackAdmin.TaskInput("season_1", "talk", actionType, "", false, 1, levels);
    }

    private static RewardTrackAdmin.PrizeInput prize(String rewardType, String extra, int productType) {
        return new RewardTrackAdmin.PrizeInput("season_1", "p1", 10, productType, rewardType, extra, 5, false, 1);
    }

    @Test
    void idsAreRequiredShortAndPlain() {
        assertNull(RewardTrackAdmin.validate(track("season_1", 0, 0)));
        assertNull(RewardTrackAdmin.validate(track("Season-2.b", 0, 0)));
        assertNotNull(RewardTrackAdmin.validate(track("", 0, 0)));
        assertNotNull(RewardTrackAdmin.validate(track("has space", 0, 0)));
        assertNotNull(RewardTrackAdmin.validate(track("x".repeat(RewardTrackAdmin.ID_MAX_LENGTH + 1), 0, 0)));
    }

    @Test
    void aTrackWindowMustBeOrdered() {
        assertNull(RewardTrackAdmin.validate(track("s", 100, 200)));
        assertNull(RewardTrackAdmin.validate(track("s", 100, 0)), "an open end is fine");
        assertNotNull(RewardTrackAdmin.validate(track("s", 200, 100)));
        assertNotNull(RewardTrackAdmin.validate(track("s", -1, 0)));
    }

    @Test
    void aTaskNeedsAKnownActionAndGrowingLevels() {
        List<RewardTrack.Level> levels =
                List.of(new RewardTrack.Level(2, 10, false), new RewardTrack.Level(4, 20, false));
        assertNull(RewardTrackAdmin.validate(task("chat_with_someone", levels)));
        assertNull(RewardTrackAdmin.validate(task("TALK_IN_ROOM", levels)), "the enum name is accepted too");
        assertNotNull(RewardTrackAdmin.validate(task("dance", levels)));
        assertNotNull(RewardTrackAdmin.validate(task("chat_with_someone", List.of())));
        assertNotNull(RewardTrackAdmin.validate(task(
                "chat_with_someone",
                List.of(new RewardTrack.Level(4, 10, false), new RewardTrack.Level(2, 20, false)))));
        assertNotNull(
                RewardTrackAdmin.validate(task("chat_with_someone", List.of(new RewardTrack.Level(0, 10, false)))));
    }

    @Test
    void aPrizeNeedsAKnownRewardTypeAndABadgeNeedsItsCode() {
        assertNull(RewardTrackAdmin.validate(prize("duckets", "", 0)));
        assertNull(RewardTrackAdmin.validate(prize(" Diamonds ", "", 0)), "case and spaces are forgiven");
        assertNull(RewardTrackAdmin.validate(prize("badge", "ACH_1", 0)));
        assertNotNull(RewardTrackAdmin.validate(prize("badge", "", 0)));
        assertNotNull(RewardTrackAdmin.validate(prize("gold", "", 0)));
        assertNull(RewardTrackAdmin.validate(prize("furni", "club_sofa", 0)));
        assertNotNull(RewardTrackAdmin.validate(prize("furni", "", 0)), "a furni prize names its item");
        assertNotNull(
                RewardTrackAdmin.validate(
                        new RewardTrackAdmin.PrizeInput("season_1", "p1", 10, 0, "furni", "club_sofa", 99, false, 1)),
                "the copies are capped");
        assertNotNull(RewardTrackAdmin.validate(prize("duckets", "", Short.MAX_VALUE + 1)), "the wire uses a short");
    }

    @Test
    void deletesNameAKnownEntity() {
        assertNull(RewardTrackAdmin.validateDelete("track", "season_1", ""));
        assertNull(RewardTrackAdmin.validateDelete("task", "season_1", "talk"));
        assertNull(RewardTrackAdmin.validateDelete("prize", "season_1", "p1"));
        assertNotNull(RewardTrackAdmin.validateDelete("task", "season_1", ""));
        assertNotNull(RewardTrackAdmin.validateDelete("level", "season_1", "x"));
    }

    @Test
    void textsNeedPlainKeysAndBoundedValues() {
        assertNull(RewardTrackAdmin.validateTexts(
                "season_1", java.util.Map.of("name", "Season 1", "task.talk.desc", "Chat")));
        assertNotNull(RewardTrackAdmin.validateTexts("season_1", java.util.Map.of("has space", "x")));
        assertNotNull(RewardTrackAdmin.validateTexts(
                "season_1", java.util.Map.of("name", "x".repeat(RewardTrackAdmin.TEXT_VALUE_MAX_LENGTH + 1))));
        assertNotNull(RewardTrackAdmin.validateTexts("", java.util.Map.of("name", "x")));
    }

    @Test
    void theChoicesFollowTheServer() {
        assertEquals(
                QuestGoalType.values().length, RewardTrackAdmin.actionTypes().size());
        assertTrue(RewardTrackAdmin.actionTypes().contains("chat_with_someone"));
        assertTrue(RewardTrackAdmin.rewardTypes().contains(QuestRewards.TYPE_BADGE));
    }
}
