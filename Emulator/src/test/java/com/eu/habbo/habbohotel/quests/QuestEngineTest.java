package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.quests.DailyQuestComposer;
import com.eu.habbo.messages.outgoing.quests.DailyTaskUpdatedComposer;
import com.eu.habbo.messages.outgoing.quests.QuestCompletedComposer;
import com.eu.habbo.messages.outgoing.quests.QuestComposer;
import com.eu.habbo.messages.outgoing.quests.QuestExpiredComposer;
import com.eu.habbo.messages.outgoing.quests.QuestsComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackProgressComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTracksComposer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The quest engine rules without a database: campaigns, the daily quest, the daily tasks and the reward track. */
class QuestEngineTest {

    private static Quest quest(
            int id,
            String campaign,
            String code,
            int order,
            QuestGoalType goal,
            int count,
            int rewardType,
            int amount,
            boolean easy,
            boolean daily) {
        return new Quest(
                id, campaign, code, "", order, goal, "", count, rewardType, amount, "", easy, daily, 0, 0, "", "1");
    }

    private static Habbo habbo(int userId, boolean club) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(userId);
        when(info.getUsername()).thenReturn("tester");
        when(info.getCredits()).thenReturn(100);
        when(info.getCurrencyAmount(anyInt())).thenReturn(100);
        when(habbo.getHabboInfo()).thenReturn(info);
        HabboStats stats = mock(HabboStats.class);
        when(stats.hasActiveClub()).thenReturn(club);
        when(habbo.getHabboStats()).thenReturn(stats);
        HabboInventory inventory = mock(HabboInventory.class);
        BadgesComponent badges = mock(BadgesComponent.class);
        when(badges.hasBadge(anyString())).thenReturn(true);
        when(inventory.getBadgesComponent()).thenReturn(badges);
        when(habbo.getInventory()).thenReturn(inventory);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        return habbo;
    }

    private static List<MessageComposer> sent(Habbo habbo) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient(), org.mockito.Mockito.atLeast(0)).sendResponse(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static QuestManager questManager() {
        QuestManager manager = new QuestManager(false) {};
        manager.register(
                quest(1, "chat", "chat_1", 1, QuestGoalType.TALK_IN_ROOM, 2, Quest.REWARD_DUCKETS, 20, true, false));
        manager.register(
                quest(2, "chat", "chat_2", 2, QuestGoalType.TALK_IN_ROOM, 5, Quest.REWARD_CREDITS, 5, false, false));
        manager.register(quest(
                3, "explore", "explore_1", 1, QuestGoalType.VISIT_ROOMS, 1, Quest.REWARD_DUCKETS, 10, true, false));
        manager.register(quest(
                10, "daily", "daily_chat", 1, QuestGoalType.TALK_IN_ROOM, 1, Quest.REWARD_DUCKETS, 10, true, true));
        manager.register(quest(
                11, "daily", "daily_visit", 2, QuestGoalType.VISIT_ROOMS, 1, Quest.REWARD_DUCKETS, 25, false, true));
        return manager;
    }

    @Test
    void theListCarriesOneEntryPerCampaignWithTheCurrentQuest() {
        QuestManager manager = questManager();
        Habbo habbo = habbo(7, false);

        List<QuestsComposer.Quest> entries = manager.campaignEntries(habbo);

        assertEquals(2, entries.size());
        assertEquals("chat", entries.get(0).getCampaignCode());
        assertEquals(1, entries.get(0).getId());
        assertEquals(0, entries.get(0).getCompletedQuestsInCampaign());
        assertEquals(2, entries.get(0).getQuestCountInCampaign());
        assertEquals("TALK_IN_ROOM", entries.get(0).getType());
        assertEquals("explore", entries.get(1).getCampaignCode());
    }

    @Test
    void acceptingTracksOneQuestAndCancelsThePreviousOne() {
        QuestManager manager = questManager();
        Habbo habbo = habbo(7, false);

        assertTrue(manager.accept(habbo, 1));
        assertFalse(manager.accept(habbo, 2), "the second quest of the chain is not the current one");
        assertTrue(manager.accept(habbo, 3));

        List<MessageComposer> packets = sent(habbo);
        assertTrue(packets.get(0) instanceof QuestComposer);
        assertTrue(packets.get(1) instanceof QuestExpiredComposer, "the previous quest is cancelled");
        assertFalse(((QuestExpiredComposer) packets.get(1)).isExpired());
        assertEquals(1, ((QuestExpiredComposer) packets.get(1)).getQuest().getId());
        assertTrue(packets.get(2) instanceof QuestComposer);
        assertEquals(3, manager.stateFor(habbo).accepted().getQuestId());
    }

    @Test
    void progressCompletesTheQuestPaysTheRewardAndAdvancesTheCampaign() {
        QuestManager manager = questManager();
        Habbo habbo = habbo(7, true);
        manager.accept(habbo, 1);

        manager.progress(habbo, QuestGoalType.VISIT_ROOMS, 1);
        assertEquals(0, manager.stateFor(habbo).get(1).getProgress(), "other goals do not count");

        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);

        List<MessageComposer> packets = sent(habbo);
        assertTrue(packets.get(1) instanceof QuestComposer);
        assertEquals(1, ((QuestComposer) packets.get(1)).getQuest().getCompletedSteps());
        QuestCompletedComposer completed = (QuestCompletedComposer) packets.get(2);
        assertTrue(completed.isShowDialog());
        assertEquals(2, completed.getQuest().getCompletedSteps());
        assertEquals(1, completed.getQuest().getCompletedQuestsInCampaign());
        // HC members earn double duckets
        verify(habbo).givePoints(eq(Quest.REWARD_DUCKETS), eq(40), anyString());
        assertNull(manager.stateFor(habbo).accepted());
        assertEquals(2, manager.campaignEntries(habbo).get(0).getId(), "the campaign moved to its second quest");
    }

    @Test
    void aFinishedCampaignIsSentAsACompletedEntry() {
        QuestManager manager = questManager();
        Habbo habbo = habbo(7, false);
        manager.accept(habbo, 3);
        manager.progress(habbo, QuestGoalType.VISIT_ROOMS, 1);

        QuestsComposer.Quest entry = manager.campaignEntries(habbo).get(1);
        assertEquals(0, entry.getId());
        assertEquals(1, entry.getCompletedQuestsInCampaign());
        assertEquals(1, entry.getQuestCountInCampaign());
        verify(habbo).givePoints(eq(Quest.REWARD_DUCKETS), eq(10), anyString());
    }

    @Test
    void theDailyQuestOffersTheOpenQuestsOfTheDayAndCancelsWithoutAnId() {
        QuestManager manager = questManager();
        Habbo habbo = habbo(7, false);

        DailyQuestComposer easy = manager.dailyQuest(habbo, true, 0);
        assertEquals(10, easy.getQuest().getId());
        assertEquals(1, easy.getEasyQuestCount());
        assertEquals(1, easy.getHardQuestCount());

        manager.accept(habbo, 11);
        assertEquals(11, manager.dailyQuest(habbo, true, 0).getQuest().getId(), "the accepted daily quest wins");
        assertTrue(manager.cancelDaily(habbo));
        assertNull(manager.stateFor(habbo).accepted());

        manager.accept(habbo, 11);
        manager.progress(habbo, QuestGoalType.VISIT_ROOMS, 1);
        DailyQuestComposer hard = manager.dailyQuest(habbo, false, 0);
        assertNull(hard.getQuest(), "the only hard daily quest is done for today");
        assertEquals(0, hard.getHardQuestCount());
    }

    @Test
    void dailyTasksCompleteThenClaimAndTheBonusCountsTheClaims() {
        DailyTaskManager manager = new DailyTaskManager(false) {};
        manager.register(
                new DailyTask(1, "talk", QuestGoalType.TALK_IN_ROOM, "", 2, false, "duckets", "", 15, "", "1", 1));
        manager.register(
                new DailyTask(2, "bonus", QuestGoalType.CLAIM_DAILY_TASK, "", 1, true, "credits", "", 5, "", "1", 2));
        Habbo habbo = habbo(7, false);

        assertFalse(manager.claim(habbo, 1), "nothing to claim yet");
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(1, manager.claimableCount(habbo));

        List<MessageComposer> packets = sent(habbo);
        DailyTaskUpdatedComposer update = (DailyTaskUpdatedComposer) packets.get(1);
        assertEquals(1, update.getTaskId());
        assertEquals(2, update.getRepeats());
        assertEquals(UserDailyTaskState.STATUS_COMPLETED, update.getStatus());

        assertTrue(manager.claim(habbo, 1));
        assertFalse(manager.claim(habbo, 1), "a claimed task cannot be claimed twice");
        verify(habbo).givePoints(eq(Quest.REWARD_DUCKETS), eq(15), anyString());
        assertEquals(0, manager.claimableCount(habbo));
        assertEquals(
                UserDailyTaskState.STATUS_CLAIMED,
                manager.activeTasks(habbo).get(0).status());
        assertTrue(manager.activeTasks(habbo).get(1).bonus());
    }

    @Test
    void reloadingTheRewardTracksDropsTheTracksAndTheCachedUserStates() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, true, 1.5, 50, 25, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(1, 10, false));
        track.addTask(talk);
        manager.register(track);
        Habbo habbo = habbo(7, false);

        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(10, manager.stateFor(habbo, track).getPoints());

        manager.reload();
        assertTrue(manager.activeTracks().isEmpty(), "the tracks are read again from the source");

        manager.register(track);
        assertEquals(0, manager.stateFor(habbo, track).getPoints(), "the cached state is dropped with the tracks");
    }

    @Test
    void staffCanHandOutAndTakeBackPointsButNeverBelowZero() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, false, 1, 0, 0, 0);
        manager.register(track);
        Habbo habbo = habbo(7, false);

        assertEquals(30, manager.adjustPoints(habbo, track, 30));
        assertEquals(10, manager.adjustPoints(habbo, track, -20));
        assertEquals(0, manager.adjustPoints(habbo, track, -50), "taking more than they have leaves zero");
        assertEquals(0, manager.stateFor(habbo, track).getPoints());
    }

    @Test
    void theRewardTrackPaysLevelPointsAndGatesThePrizes() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, true, 1.5, 50, 25, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(2, 10, false));
        talk.addLevel(new RewardTrack.Level(4, 20, false));
        track.addTask(talk);
        track.addPrize(new RewardTrack.Prize("p1", 10, 0, "duckets", "", 50, false, 1));
        track.addPrize(new RewardTrack.Prize("p1_premium", 10, 0, "diamonds", "", 5, true, 2));
        track.addPrize(new RewardTrack.Prize("p2", 30, 0, "credits", "", 10, false, 3));
        manager.register(track);
        Habbo habbo = habbo(7, false);

        assertEquals(QuestGoalType.TALK_IN_ROOM, talk.getGoalType());
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(10, manager.stateFor(habbo, track).getPoints());

        List<MessageComposer> packets = sent(habbo);
        RewardTrackProgressComposer progress = (RewardTrackProgressComposer) packets.get(1);
        assertEquals("talk", progress.getTaskId());
        assertEquals(2, progress.getProgressCount());
        assertEquals(10, progress.getPoints());

        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(habbo, "season_1", "p1"));
        assertEquals(RewardTrackManager.RESULT_ALREADY_CLAIMED, manager.claim(habbo, "season_1", "p1"));
        assertEquals(RewardTrackManager.RESULT_PREMIUM_REQUIRED, manager.claim(habbo, "season_1", "p1_premium"));
        assertEquals(RewardTrackManager.RESULT_NOT_ENOUGH_POINTS, manager.claim(habbo, "season_1", "p2"));
        assertEquals(RewardTrackManager.RESULT_UNKNOWN, manager.claim(habbo, "season_1", "nope"));
        verify(habbo).givePoints(eq(Quest.REWARD_DUCKETS), eq(50), anyString());

        RewardTracksComposer.Track wire = manager.toWire(track, manager.stateFor(habbo, track));
        assertEquals(2, wire.tasks().get(0).progressCount());
        assertTrue(wire.prizes().get(0).claimed());
        assertFalse(wire.prizes().get(1).available(), "premium prizes stay locked without the pass");
        assertFalse(wire.complete());

        assertEquals(RewardTrackManager.RESULT_OK, manager.purchasePremium(habbo, "season_1"));
        assertEquals(RewardTrackManager.RESULT_ALREADY_PREMIUM, manager.purchasePremium(habbo, "season_1"));
        verify(habbo).givePoints(eq(QuestRewards.DIAMONDS_POINT_TYPE), eq(-25), anyString());
        assertEquals(60, manager.stateFor(habbo, track).getPoints(), "the instant points are added");
        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(habbo, "season_1", "p1_premium"));
        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(habbo, "season_1", "p2"));
        assertTrue(manager.toWire(track, manager.stateFor(habbo, track)).premiumComplete());

        // the premium boost applies to the next level
        manager.progress(habbo, QuestGoalType.TALK_IN_ROOM, 2);
        assertEquals(90, manager.stateFor(habbo, track).getPoints());
    }
}
