package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.quests.DailyQuestComposer;
import com.eu.habbo.messages.outgoing.quests.QuestCompletedComposer;
import com.eu.habbo.messages.outgoing.quests.QuestComposer;
import com.eu.habbo.messages.outgoing.quests.QuestExpiredComposer;
import com.eu.habbo.messages.outgoing.quests.QuestsComposer;
import com.eu.habbo.messages.outgoing.quests.SeasonalQuestsComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR 13 quest engine: campaign quests (one current quest per campaign), the daily quest pool and
 * the progress hooks. Definitions live in `quests`, progress in `users_quests`.
 */
public class QuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestManager.class);

    /** Per-user quest state, cached while the user is online. */
    public static final class UserQuestState {
        private final int userId;
        private final Map<Integer, UserQuestProgress> progress = new ConcurrentHashMap<>();

        public UserQuestState(int userId) {
            this.userId = userId;
        }

        public int getUserId() {
            return this.userId;
        }

        public UserQuestProgress get(int questId) {
            return this.progress.get(questId);
        }

        public UserQuestProgress getOrCreate(int questId) {
            return this.progress.computeIfAbsent(questId, id -> new UserQuestProgress(id, 0, false, 0, 0));
        }

        /** The quest the tracker follows, at most one at a time. */
        public UserQuestProgress accepted() {
            for (UserQuestProgress entry : this.progress.values()) {
                if (entry.isAccepted() && !entry.isCompleted()) {
                    return entry;
                }
            }
            return null;
        }

        public boolean isCompleted(int questId) {
            UserQuestProgress entry = this.progress.get(questId);
            return entry != null && entry.isCompleted();
        }

        public int completedCount(List<Quest> quests) {
            int count = 0;
            for (Quest quest : quests) {
                if (this.isCompleted(quest.getId())) {
                    count++;
                }
            }
            return count;
        }

        public void put(UserQuestProgress entry) {
            this.progress.put(entry.getQuestId(), entry);
        }
    }

    private final Map<Integer, Quest> quests = new LinkedHashMap<>();
    private final Map<String, List<Quest>> campaigns = new LinkedHashMap<>();
    private final List<Quest> dailyPool = new ArrayList<>();
    private final Map<Integer, UserQuestState> users = new ConcurrentHashMap<>();
    private final boolean persistent;

    public QuestManager() {
        this(true);
        this.reload();
    }

    /** A manager without a database behind it, for tests: definitions and states are registered by hand. */
    protected QuestManager(boolean persistent) {
        this.persistent = persistent;
    }

    public synchronized void reload() {
        this.quests.clear();
        this.campaigns.clear();
        this.dailyPool.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM quests WHERE enabled = 1 ORDER BY campaign_code, sort_order, id");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                Quest quest = Quest.fromResultSet(set);
                if (quest.getGoalType() == null) {
                    LOGGER.warn("Quest {} has an unknown goal type, skipped", quest.getId());
                    continue;
                }
                this.register(quest);
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the quests", exception);
        }
        LOGGER.info(
                "Quest Manager -> Loaded! ({} quests in {} campaigns, {} daily)",
                this.quests.size(),
                this.campaigns.size(),
                this.dailyPool.size());
    }

    /** Registers a definition. */
    public synchronized void register(Quest quest) {
        this.quests.put(quest.getId(), quest);
        if (quest.isDaily()) {
            this.dailyPool.add(quest);
            this.dailyPool.sort(Comparator.comparingInt(Quest::getSortOrder).thenComparingInt(Quest::getId));
            return;
        }
        List<Quest> chain = this.campaigns.computeIfAbsent(quest.getCampaignCode(), code -> new ArrayList<>());
        chain.add(quest);
        chain.sort(Comparator.comparingInt(Quest::getSortOrder).thenComparingInt(Quest::getId));
    }

    public Quest getQuest(int id) {
        return this.quests.get(id);
    }

    public Map<String, List<Quest>> getCampaigns() {
        return Collections.unmodifiableMap(this.campaigns);
    }

    public List<Quest> getDailyPool() {
        return Collections.unmodifiableList(this.dailyPool);
    }

    // ------------------------------------------------------------------ user state

    public UserQuestState stateFor(Habbo habbo) {
        return this.stateFor(habbo.getHabboInfo().getId());
    }

    public UserQuestState stateFor(int userId) {
        return this.users.computeIfAbsent(userId, this::load);
    }

    public void unload(int userId) {
        this.users.remove(userId);
    }

    private UserQuestState load(int userId) {
        UserQuestState state = new UserQuestState(userId);
        if (!this.persistent) {
            return state;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM users_quests WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    state.put(new UserQuestProgress(
                            set.getInt("quest_id"),
                            set.getInt("progress"),
                            set.getBoolean("accepted"),
                            set.getInt("accepted_at"),
                            set.getInt("completed_at")));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the quests of user {}", userId, exception);
        }
        return state;
    }

    protected void save(int userId, UserQuestProgress entry) {
        if (!this.persistent) {
            return;
        }
        int progress = entry.getProgress();
        boolean accepted = entry.isAccepted();
        int acceptedAt = entry.getAcceptedAt();
        int completedAt = entry.getCompletedAt();
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_quests (user_id, quest_id, progress, accepted, accepted_at, completed_at)"
                                    + " VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE progress = VALUES(progress),"
                                    + " accepted = VALUES(accepted), accepted_at = VALUES(accepted_at),"
                                    + " completed_at = VALUES(completed_at)")) {
                statement.setInt(1, userId);
                statement.setInt(2, entry.getQuestId());
                statement.setInt(3, progress);
                statement.setBoolean(4, accepted);
                statement.setInt(5, acceptedAt);
                statement.setInt(6, completedAt);
                statement.execute();
            } catch (SQLException exception) {
                LOGGER.error("Could not save quest {} of user {}", entry.getQuestId(), userId, exception);
            }
        });
    }

    // ------------------------------------------------------------------ wire helpers

    /** The current quest of a campaign: the first one the user has not completed, null when the campaign is done. */
    public Quest currentQuest(String campaignCode, UserQuestState state) {
        List<Quest> chain = this.campaigns.get(campaignCode);
        if (chain == null) {
            return null;
        }
        for (Quest quest : chain) {
            if (!state.isCompleted(quest.getId())) {
                return quest;
            }
        }
        return null;
    }

    public QuestsComposer.Quest toWire(Quest quest, UserQuestState state) {
        List<Quest> chain =
                quest.isDaily() ? this.dailyPool : this.campaigns.getOrDefault(quest.getCampaignCode(), List.of());
        UserQuestProgress entry = state.get(quest.getId());
        int completedInCampaign = quest.isDaily() ? this.completedTodayCount(state) : state.completedCount(chain);
        int completedSteps = entry == null ? 0 : Math.min(entry.getProgress(), quest.getGoalCount());
        if (entry != null && entry.isCompleted()) {
            completedSteps = quest.getGoalCount();
        }
        return new QuestsComposer.Quest(
                quest.getCampaignCode(),
                completedInCampaign,
                chain.size(),
                quest.getRewardType(),
                quest.getId(),
                entry != null && entry.isAccepted() && !entry.isCompleted(),
                quest.getTypeCode(),
                quest.getImageVersion(),
                quest.getRewardAmount(),
                quest.getCode(),
                completedSteps,
                quest.getGoalCount(),
                quest.getSortOrder(),
                quest.getCatalogPageName(),
                quest.getChainCode(),
                quest.isEasy(),
                quest.isSeasonal(),
                quest.isSeasonal() ? this.secondsLeft(quest, entry) : 0);
    }

    private int secondsLeft(Quest quest, UserQuestProgress entry) {
        if (entry == null || !entry.isAccepted() || entry.getAcceptedAt() < 1) {
            return quest.getSeasonalSeconds();
        }
        return Math.max(0, quest.getSeasonalSeconds() - (Emulator.getIntUnixTimestamp() - entry.getAcceptedAt()));
    }

    /** A completed campaign is an entry with id 0 and completed == total; the client shows "completed". */
    static QuestsComposer.Quest completedCampaignWire(String campaignCode, int questCount) {
        return completedCampaignWire(campaignCode, questCount, 0);
    }

    /**
     * The seasonal calendar places every entry on the day of its sort order, a finished day too
     * (Calendar.onQuests reads it from the completed entry), so a completed seasonal campaign keeps
     * the sort order of its last quest.
     */
    static QuestsComposer.Quest completedCampaignWire(String campaignCode, int questCount, int sortOrder) {
        return new QuestsComposer.Quest(
                campaignCode,
                questCount,
                questCount,
                0,
                0,
                false,
                "",
                "",
                0,
                "",
                0,
                0,
                sortOrder,
                "",
                "",
                false,
                false,
                0);
    }

    /** A campaign of the seasonal calendar: its quests carry a seasonal countdown. */
    static boolean isSeasonalCampaign(List<Quest> chain) {
        for (Quest quest : chain) {
            if (quest.isSeasonal()) {
                return true;
            }
        }
        return false;
    }

    /** One entry per campaign, the official list layout; seasonal campaigns go to the seasonal list. */
    public List<QuestsComposer.Quest> campaignEntries(Habbo habbo) {
        UserQuestState state = this.stateFor(habbo);
        List<QuestsComposer.Quest> entries = new ArrayList<>();
        for (Map.Entry<String, List<Quest>> campaign : this.campaigns.entrySet()) {
            Quest current = this.currentQuest(campaign.getKey(), state);
            if (current == null) {
                if (!isSeasonalCampaign(campaign.getValue())) {
                    entries.add(completedCampaignWire(
                            campaign.getKey(), campaign.getValue().size()));
                }
            } else if (!current.isSeasonal()) {
                entries.add(this.toWire(current, state));
            }
        }
        return entries;
    }

    public List<QuestsComposer.Quest> seasonalEntries(Habbo habbo) {
        UserQuestState state = this.stateFor(habbo);
        List<QuestsComposer.Quest> entries = new ArrayList<>();
        for (Map.Entry<String, List<Quest>> campaign : this.campaigns.entrySet()) {
            List<Quest> chain = campaign.getValue();
            Quest current = this.currentQuest(campaign.getKey(), state);
            if (current != null && current.isSeasonal()) {
                entries.add(this.toWire(current, state));
            } else if (current == null && !chain.isEmpty() && isSeasonalCampaign(chain)) {
                entries.add(completedCampaignWire(
                        campaign.getKey(),
                        chain.size(),
                        chain.get(chain.size() - 1).getSortOrder()));
            }
        }
        return entries;
    }

    // ------------------------------------------------------------------ actions

    public void sendQuests(Habbo habbo, boolean openWindow) {
        habbo.getClient().sendResponse(new QuestsComposer(this.campaignEntries(habbo), openWindow));
    }

    public void sendSeasonalQuests(Habbo habbo) {
        habbo.getClient().sendResponse(new SeasonalQuestsComposer(this.seasonalEntries(habbo)));
    }

    /** OpenQuestTracker: the client asks for the quest it should track. */
    public void sendTrackedQuest(Habbo habbo) {
        UserQuestState state = this.stateFor(habbo);
        UserQuestProgress accepted = state.accepted();
        if (accepted == null) {
            return;
        }
        Quest quest = this.quests.get(accepted.getQuestId());
        if (quest != null) {
            habbo.getClient().sendResponse(new QuestComposer(this.toWire(quest, state)));
        }
    }

    /** StartCampaign: accept the current quest of the campaign (the tracker auto-start of the default one). */
    public boolean startCampaign(Habbo habbo, String campaignCode) {
        Quest quest = this.currentQuest(campaignCode, this.stateFor(habbo));
        return quest != null && this.accept(habbo, quest.getId());
    }

    /** AcceptQuest / ActivateQuest. Only one quest is tracked at a time; the previous one is cancelled. */
    public boolean accept(Habbo habbo, int questId) {
        Quest quest = this.quests.get(questId);
        if (quest == null) {
            return false;
        }
        UserQuestState state = this.stateFor(habbo);
        if (quest.isDaily()
                ? this.completedToday(state, quest)
                : this.currentQuest(quest.getCampaignCode(), state) != quest) {
            return false;
        }
        int now = Emulator.getIntUnixTimestamp();
        UserQuestProgress previous = state.accepted();
        if (previous != null && previous.getQuestId() != questId) {
            previous.setAccepted(false, now);
            this.save(state.getUserId(), previous);
            Quest previousQuest = this.quests.get(previous.getQuestId());
            if (previousQuest != null) {
                habbo.getClient().sendResponse(new QuestExpiredComposer(false, this.toWire(previousQuest, state)));
            }
        }
        UserQuestProgress entry = state.getOrCreate(questId);
        if (quest.isDaily() && entry.isCompleted()) {
            // a daily quest completed on an earlier day starts over
            entry.setCompletedAt(0);
            entry.setProgress(0);
        }
        entry.setAccepted(true, now);
        this.save(state.getUserId(), entry);
        habbo.getClient().sendResponse(new QuestComposer(this.toWire(quest, state)));
        return true;
    }

    /** RejectQuest: stop tracking; the progress is kept so the user can resume later. */
    public boolean cancel(Habbo habbo, int questId) {
        UserQuestState state = this.stateFor(habbo);
        UserQuestProgress entry = state.get(questId);
        Quest quest = this.quests.get(questId);
        if (entry == null || quest == null || !entry.isAccepted()) {
            return false;
        }
        entry.setAccepted(false, Emulator.getIntUnixTimestamp());
        this.save(state.getUserId(), entry);
        habbo.getClient().sendResponse(new QuestExpiredComposer(false, this.toWire(quest, state)));
        return true;
    }

    /** CancelDailyQuest: the daily widget's cancel has no argument. */
    public boolean cancelDaily(Habbo habbo) {
        UserQuestProgress accepted = this.stateFor(habbo).accepted();
        if (accepted == null) {
            return false;
        }
        Quest quest = this.quests.get(accepted.getQuestId());
        if (quest == null || !quest.isDaily()) {
            return false;
        }
        return this.cancel(habbo, quest.getId());
    }

    // ------------------------------------------------------------------ daily quest

    static int startOfTodayUtc() {
        return (int) LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private boolean completedToday(UserQuestState state, Quest quest) {
        UserQuestProgress entry = state.get(quest.getId());
        return entry != null && entry.isCompleted() && entry.getCompletedAt() >= startOfTodayUtc();
    }

    private int completedTodayCount(UserQuestState state) {
        int count = 0;
        for (Quest quest : this.dailyPool) {
            if (this.completedToday(state, quest)) {
                count++;
            }
        }
        return count;
    }

    /** The daily quests still open today, by difficulty. */
    public List<Quest> openDailyQuests(UserQuestState state, boolean easy) {
        List<Quest> open = new ArrayList<>();
        for (Quest quest : this.dailyPool) {
            if (quest.isEasy() == easy && !this.completedToday(state, quest)) {
                open.add(quest);
            }
        }
        return open;
    }

    /** GetDailyQuest(easy, index): the accepted daily quest wins, else the index-th open one. */
    public DailyQuestComposer dailyQuest(Habbo habbo, boolean easy, int index) {
        UserQuestState state = this.stateFor(habbo);
        List<Quest> easyOpen = this.openDailyQuests(state, true);
        List<Quest> hardOpen = this.openDailyQuests(state, false);
        UserQuestProgress accepted = state.accepted();
        Quest quest = null;
        if (accepted != null) {
            Quest tracked = this.quests.get(accepted.getQuestId());
            if (tracked != null && tracked.isDaily()) {
                quest = tracked;
            }
        }
        if (quest == null) {
            List<Quest> pool = easy ? easyOpen : hardOpen;
            if (!pool.isEmpty()) {
                quest = pool.get(Math.floorMod(Math.max(0, index), pool.size()));
            }
        }
        return new DailyQuestComposer(
                quest == null ? null : this.toWire(quest, state), easyOpen.size(), hardOpen.size());
    }

    public void sendDailyQuest(Habbo habbo, boolean easy, int index) {
        habbo.getClient().sendResponse(this.dailyQuest(habbo, easy, index));
    }

    // ------------------------------------------------------------------ progress

    /** Adds progress to the tracked quest when its goal matches; completes it and pays the reward. */
    public void progress(Habbo habbo, QuestGoalType goalType, int amount) {
        if (habbo == null || goalType == null || amount < 1 || this.quests.isEmpty()) {
            return;
        }
        UserQuestState state = this.stateFor(habbo);
        UserQuestProgress entry = state.accepted();
        if (entry == null) {
            return;
        }
        Quest quest = this.quests.get(entry.getQuestId());
        if (quest == null || quest.getGoalType() != goalType) {
            return;
        }
        entry.setProgress(entry.getProgress() + amount);
        if (entry.getProgress() >= quest.getGoalCount()) {
            this.complete(habbo, quest, state, entry);
            return;
        }
        this.save(state.getUserId(), entry);
        habbo.getClient().sendResponse(new QuestComposer(this.toWire(quest, state)));
    }

    private void complete(Habbo habbo, Quest quest, UserQuestState state, UserQuestProgress entry) {
        entry.setProgress(quest.getGoalCount());
        entry.setCompletedAt(Emulator.getIntUnixTimestamp());
        entry.setAccepted(false, entry.getCompletedAt());
        this.save(state.getUserId(), entry);
        QuestRewards.grantActivityPoints(habbo, quest.getRewardType(), quest.getRewardAmount());
        QuestRewards.grantBadge(habbo, quest.getRewardBadge());
        habbo.getClient().sendResponse(new QuestCompletedComposer(this.toWire(quest, state), true));
        QuestProgressEvents.questCompleted(habbo);
    }
}
