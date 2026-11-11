package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.TalentShowRespect.Verdict;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;

/** The talent show's respect rule: who may be given respect in a room with a stage. */
class TalentShowRespectTest {
    @Test
    void aRoomWithoutAStageGivesRespectAsAlways() {
        assertEquals(Verdict.ALLOWED, TalentShowRespect.verdict(false, false, 1, 5));
    }

    @Test
    void respectGoesOnlyToAUserOnTheStage() {
        assertEquals(Verdict.NOT_ON_STAGE, TalentShowRespect.verdict(true, false, 20, 5));
        assertEquals(Verdict.ALLOWED, TalentShowRespect.verdict(true, true, 20, 5));
    }

    @Test
    void theShowNeedsAnAudience() {
        assertEquals(Verdict.NOT_ENOUGH_USERS, TalentShowRespect.verdict(true, true, 4, 5));
        assertEquals(Verdict.ALLOWED, TalentShowRespect.verdict(true, true, 5, 5));
        assertEquals(Verdict.ALLOWED, TalentShowRespect.verdict(true, true, 1, 0));
    }

    @Test
    void theStageIsCheckedBeforeTheAudience() {
        assertEquals(Verdict.NOT_ON_STAGE, TalentShowRespect.verdict(true, false, 1, 5));
    }

    @Test
    void eachRefusalHasTheOfficialNotice() {
        assertEquals(0, Verdict.ALLOWED.notification());
        assertEquals(CustomNotificationComposer.STARS_NOT_CANDIDATE, Verdict.NOT_ON_STAGE.notification());
        assertEquals(CustomNotificationComposer.STARS_NOT_ENOUGH_USERS, Verdict.NOT_ENOUGH_USERS.notification());
    }

    @Test
    void readsTheStageFurniOfTheSetting() {
        assertEquals(
                Set.of("hs_stage_blue", "hs_circstage"),
                TalentShowRespect.stageNames(" HS_Stage_Blue ;; hs_circstage,"));
        assertEquals(Set.of(), TalentShowRespect.stageNames(null));
    }
}
