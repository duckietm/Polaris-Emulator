package polaris.hotel.quests;

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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.quests.DailyQuestComposer;
import polaris.messages.outgoing.quests.DailyTaskUpdatedComposer;
import polaris.messages.outgoing.quests.QuestCompletedComposer;
import polaris.messages.outgoing.quests.QuestComposer;
import polaris.messages.outgoing.quests.QuestExpiredComposer;
import polaris.messages.outgoing.quests.QuestsComposer;
import polaris.messages.outgoing.quests.RewardTrackProgressComposer;
import polaris.messages.outgoing.quests.RewardTracksComposer;

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

    private static User user(int userId, boolean club) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(userId);
        when(info.getUsername()).thenReturn("tester");
        when(info.getCredits()).thenReturn(100);
        when(info.getCurrencyAmount(anyInt())).thenReturn(100);
        when(user.getUserInfo()).thenReturn(info);
        UserStats stats = mock(UserStats.class);
        when(stats.hasActiveClub()).thenReturn(club);
        when(user.getUserStats()).thenReturn(stats);
        UserInventory inventory = mock(UserInventory.class);
        BadgesComponent badges = mock(BadgesComponent.class);
        when(badges.hasBadge(anyString())).thenReturn(true);
        when(inventory.getBadgesComponent()).thenReturn(badges);
        when(user.getInventory()).thenReturn(inventory);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        return user;
    }

    private static List<MessageComposer> sent(User user) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(user.getClient(), org.mockito.Mockito.atLeast(0)).sendResponse(captor.capture());
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
        User user = user(7, false);

        List<QuestsComposer.Quest> entries = manager.campaignEntries(user);

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
        User user = user(7, false);

        assertTrue(manager.accept(user, 1));
        assertFalse(manager.accept(user, 2), "the second quest of the chain is not the current one");
        assertTrue(manager.accept(user, 3));

        List<MessageComposer> packets = sent(user);
        assertTrue(packets.get(0) instanceof QuestComposer);
        assertTrue(packets.get(1) instanceof QuestExpiredComposer, "the previous quest is cancelled");
        assertFalse(((QuestExpiredComposer) packets.get(1)).isExpired());
        assertEquals(1, ((QuestExpiredComposer) packets.get(1)).getQuest().getId());
        assertTrue(packets.get(2) instanceof QuestComposer);
        assertEquals(3, manager.stateFor(user).accepted().getQuestId());
    }

    @Test
    void progressCompletesTheQuestPaysTheRewardAndAdvancesTheCampaign() {
        QuestManager manager = questManager();
        User user = user(7, true);
        manager.accept(user, 1);

        manager.progress(user, QuestGoalType.VISIT_ROOMS, 1);
        assertEquals(0, manager.stateFor(user).get(1).getProgress(), "other goals do not count");

        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);

        List<MessageComposer> packets = sent(user);
        assertTrue(packets.get(1) instanceof QuestComposer);
        assertEquals(1, ((QuestComposer) packets.get(1)).getQuest().getCompletedSteps());
        QuestCompletedComposer completed = (QuestCompletedComposer) packets.get(2);
        assertTrue(completed.isShowDialog());
        assertEquals(2, completed.getQuest().getCompletedSteps());
        assertEquals(1, completed.getQuest().getCompletedQuestsInCampaign());
        // HC members earn double duckets
        verify(user).givePoints(eq(Quest.REWARD_DUCKETS), eq(40), anyString());
        assertNull(manager.stateFor(user).accepted());
        assertEquals(2, manager.campaignEntries(user).get(0).getId(), "the campaign moved to its second quest");
    }

    @Test
    void aFinishedCampaignIsSentAsACompletedEntry() {
        QuestManager manager = questManager();
        User user = user(7, false);
        manager.accept(user, 3);
        manager.progress(user, QuestGoalType.VISIT_ROOMS, 1);

        QuestsComposer.Quest entry = manager.campaignEntries(user).get(1);
        assertEquals(0, entry.getId());
        assertEquals(1, entry.getCompletedQuestsInCampaign());
        assertEquals(1, entry.getQuestCountInCampaign());
        verify(user).givePoints(eq(Quest.REWARD_DUCKETS), eq(10), anyString());
    }

    @Test
    void aFinishedSeasonalDayStaysOnTheCalendarWithItsDay() {
        QuestManager manager = questManager();
        manager.register(new Quest(
                20,
                "xmas_1",
                "xmas_visit",
                "",
                1,
                QuestGoalType.VISIT_ROOMS,
                "",
                1,
                Quest.REWARD_DUCKETS,
                5,
                "",
                true,
                false,
                86400,
                0,
                "",
                "1"));
        manager.register(new Quest(
                21,
                "xmas_2",
                "xmas_chat",
                "",
                2,
                QuestGoalType.TALK_IN_ROOM,
                "",
                1,
                Quest.REWARD_DUCKETS,
                5,
                "",
                true,
                false,
                86400,
                0,
                "",
                "1"));
        User user = user(7, false);
        manager.accept(user, 20);
        manager.progress(user, QuestGoalType.VISIT_ROOMS, 1);

        List<QuestsComposer.Quest> seasonal = manager.seasonalEntries(user);
        assertEquals(2, seasonal.size());
        assertEquals("xmas_1", seasonal.get(0).getCampaignCode());
        assertEquals(0, seasonal.get(0).getId(), "the finished day is a completed entry");
        assertEquals(1, seasonal.get(0).getSortOrder(), "and keeps its day");
        assertEquals(21, seasonal.get(1).getId());
        assertTrue(
                manager.campaignEntries(user).stream()
                        .noneMatch(entry -> entry.getCampaignCode().startsWith("xmas_")),
                "seasonal days never show in the regular list");
    }

    @Test
    void theDailyQuestOffersTheOpenQuestsOfTheDayAndCancelsWithoutAnId() {
        QuestManager manager = questManager();
        User user = user(7, false);

        DailyQuestComposer easy = manager.dailyQuest(user, true, 0);
        assertEquals(10, easy.getQuest().getId());
        assertEquals(1, easy.getEasyQuestCount());
        assertEquals(1, easy.getHardQuestCount());

        manager.accept(user, 11);
        assertEquals(11, manager.dailyQuest(user, true, 0).getQuest().getId(), "the accepted daily quest wins");
        assertTrue(manager.cancelDaily(user));
        assertNull(manager.stateFor(user).accepted());

        manager.accept(user, 11);
        manager.progress(user, QuestGoalType.VISIT_ROOMS, 1);
        DailyQuestComposer hard = manager.dailyQuest(user, false, 0);
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
        User user = user(7, false);

        assertFalse(manager.claim(user, 1), "nothing to claim yet");
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(1, manager.claimableCount(user));

        List<MessageComposer> packets = sent(user);
        DailyTaskUpdatedComposer update = (DailyTaskUpdatedComposer) packets.get(1);
        assertEquals(1, update.getTaskId());
        assertEquals(2, update.getRepeats());
        assertEquals(UserDailyTaskState.STATUS_COMPLETED, update.getStatus());

        assertTrue(manager.claim(user, 1));
        assertFalse(manager.claim(user, 1), "a claimed task cannot be claimed twice");
        verify(user)
                .givePoints(
                        eq(Quest.REWARD_DUCKETS),
                        eq(15),
                        anyString(),
                        eq("daily_task:7:" + DailyTaskManager.today() + ":1"),
                        eq(7));
        assertEquals(0, manager.claimableCount(user));
        assertEquals(
                UserDailyTaskState.STATUS_CLAIMED,
                manager.activeTasks(user).get(0).status());
        assertTrue(manager.activeTasks(user).get(1).bonus());
    }

    @Test
    void aClaimTheDatabaseRefusesIsUndoneAndPaysNothing() {
        DailyTaskManager manager = new DailyTaskManager(false) {
            @Override
            protected boolean write(int userId, UserDailyTaskState state) {
                return state.getStatus() != UserDailyTaskState.STATUS_CLAIMED;
            }
        };
        manager.register(
                new DailyTask(1, "talk", QuestGoalType.TALK_IN_ROOM, "", 1, false, "duckets", "", 15, "", "1", 1));
        User user = user(7, false);
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);

        assertFalse(manager.claim(user, 1));

        assertEquals(1, manager.claimableCount(user), "the task can still be claimed");
        verify(user, times(0)).givePoints(anyInt(), anyInt(), anyString(), anyString(), anyInt());
        verify(user, times(0)).givePoints(anyInt(), anyInt(), anyString());
    }

    @Test
    void reloadingTheRewardTracksDropsTheTracksAndTheCachedUserStates() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, true, 1.5, 50, 25, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(1, 10, false));
        track.addTask(talk);
        manager.register(track);
        User user = user(7, false);

        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(10, manager.stateFor(user, track).getPoints());

        manager.reload();
        assertTrue(manager.activeTracks().isEmpty(), "the tracks are read again from the source");

        manager.register(track);
        assertEquals(0, manager.stateFor(user, track).getPoints(), "the cached state is dropped with the tracks");
    }

    @Test
    void aTaskPutBackToZeroNeverPaysItsLevelsAgain() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, false, 1, 0, 0, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(2, 10, false));
        track.addTask(talk);
        manager.register(track);
        User user = user(7, false);

        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 2);
        assertEquals(10, manager.stateFor(user, track).getPoints());

        manager.resetTasks(user, track, List.of(talk));
        assertEquals(0, manager.stateFor(user, track).progressOf("talk"));
        assertEquals(2, manager.stateFor(user, track).peakOf("talk"));

        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 2);
        assertEquals(10, manager.stateFor(user, track).getPoints(), "chatting back up pays nothing twice");
    }

    @Test
    void aWiredTaskIsNeverMovedByPlay() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, false, 1, 0, 0, 0);
        RewardTrack.Task games = new RewardTrack.Task("games", "wired", "", false, 1);
        games.addLevel(new RewardTrack.Level(1, 10, false));
        track.addTask(games);
        manager.register(track);
        User user = user(7, false);

        assertEquals(QuestGoalType.WIRED, games.getGoalType());
        for (QuestGoalType type : QuestGoalType.values()) {
            if (type != QuestGoalType.WIRED) {
                manager.progress(user, type, 5);
            }
        }
        assertEquals(0, manager.stateFor(user, track).progressOf("games"));
        assertEquals(1, manager.setTaskProgress(user, track, games, 1));
        assertEquals(10, manager.stateFor(user, track).getPoints());
    }

    @Test
    void staffCanHandOutAndTakeBackPointsButNeverBelowZero() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, false, 1, 0, 0, 0);
        manager.register(track);
        User user = user(7, false);

        assertEquals(30, manager.adjustPoints(user, track, 30));
        assertEquals(10, manager.adjustPoints(user, track, -20));
        assertEquals(0, manager.adjustPoints(user, track, -50), "taking more than they have leaves zero");
        assertEquals(0, manager.stateFor(user, track).getPoints());
    }

    @Test
    void theRewardTrackPaysLevelPointsAndGatesThePrizes() {
        List<EconomyOperation> payments = new ArrayList<>();
        RewardTrackManager manager = new RewardTrackManager(false) {
            @Override
            protected void applyPayment(User payer, List<EconomyOperation> operations) {
                payments.addAll(operations);
            }
        };
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, true, 1.5, 50, 25, 0);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 1);
        talk.addLevel(new RewardTrack.Level(2, 10, false));
        talk.addLevel(new RewardTrack.Level(4, 20, false));
        track.addTask(talk);
        track.addPrize(new RewardTrack.Prize("p1", 10, 0, "duckets", "", 50, false, 1));
        track.addPrize(new RewardTrack.Prize("p1_premium", 10, 0, "diamonds", "", 5, true, 2));
        track.addPrize(new RewardTrack.Prize("p2", 30, 0, "credits", "", 10, false, 3));
        manager.register(track);
        User user = user(7, false);

        assertEquals(QuestGoalType.TALK_IN_ROOM, talk.getGoalType());
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 1);
        assertEquals(10, manager.stateFor(user, track).getPoints());

        List<MessageComposer> packets = sent(user);
        RewardTrackProgressComposer progress = (RewardTrackProgressComposer) packets.get(1);
        assertEquals("talk", progress.getTaskId());
        assertEquals(2, progress.getProgressCount());
        assertEquals(10, progress.getPoints());

        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(user, "season_1", "p1"));
        assertEquals(RewardTrackManager.CLAIM_ALREADY_CLAIMED, manager.claim(user, "season_1", "p1"));
        assertEquals(RewardTrackManager.CLAIM_PREMIUM_REQUIRED, manager.claim(user, "season_1", "p1_premium"));
        assertEquals(RewardTrackManager.CLAIM_NOT_ENOUGH_POINTS, manager.claim(user, "season_1", "p2"));
        assertEquals(RewardTrackManager.CLAIM_REWARD_NOT_FOUND, manager.claim(user, "season_1", "nope"));
        verify(user).givePoints(eq(Quest.REWARD_DUCKETS), eq(50), anyString(), eq("reward_track:7:season_1:p1"), eq(7));

        RewardTracksComposer.Track wire = manager.toWire(track, manager.stateFor(user, track));
        assertEquals(2, wire.tasks().get(0).progressCount());
        assertTrue(wire.prizes().get(0).claimed());
        assertFalse(wire.prizes().get(1).available(), "premium prizes stay locked without the pass");
        assertFalse(wire.complete());

        assertEquals(RewardTrackManager.RESULT_OK, manager.purchasePremium(user, "season_1"));
        assertEquals(RewardTrackManager.PREMIUM_ALREADY_OWNED, manager.purchasePremium(user, "season_1"));
        assertEquals(1, payments.size(), "the diamonds are paid through the ledger once");
        assertEquals(QuestRewards.DIAMONDS_POINT_TYPE, payments.get(0).currencyType());
        assertEquals(-25, payments.get(0).delta());
        assertEquals(60, manager.stateFor(user, track).getPoints(), "the instant points are added");
        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(user, "season_1", "p1_premium"));
        assertEquals(RewardTrackManager.RESULT_OK, manager.claim(user, "season_1", "p2"));
        assertTrue(manager.toWire(track, manager.stateFor(user, track)).premiumComplete());

        // the premium boost applies to the next level
        manager.progress(user, QuestGoalType.TALK_IN_ROOM, 2);
        assertEquals(90, manager.stateFor(user, track).getPoints());
    }
}
