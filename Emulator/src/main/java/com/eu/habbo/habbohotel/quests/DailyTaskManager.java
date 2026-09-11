package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.quests.ActiveDailyTasksComposer;
import com.eu.habbo.messages.outgoing.quests.DailyTaskUpdatedComposer;
import com.eu.habbo.messages.outgoing.quests.DailyTasksAddedComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
 * The AIR 13 daily tasks ("daily reward" window): every enabled task is offered every UTC day, the
 * user repeats it, claims the reward, and the bonus task counts the claims.
 */
public class DailyTaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyTaskManager.class);

    /** The tasks of one user for one day. */
    public static final class UserDailyTasks {
        private final int userId;
        private final String day;
        private final Map<Integer, UserDailyTaskState> tasks = new ConcurrentHashMap<>();

        public UserDailyTasks(int userId, String day) {
            this.userId = userId;
            this.day = day;
        }

        public int getUserId() {
            return this.userId;
        }

        public String getDay() {
            return this.day;
        }

        public UserDailyTaskState get(int taskId) {
            return this.tasks.get(taskId);
        }

        public UserDailyTaskState getOrCreate(int taskId) {
            return this.tasks.computeIfAbsent(
                    taskId, id -> new UserDailyTaskState(id, this.day, 0, UserDailyTaskState.STATUS_OPEN));
        }

        public void put(UserDailyTaskState state) {
            this.tasks.put(state.getTaskId(), state);
        }
    }

    private final Map<Integer, DailyTask> tasks = new LinkedHashMap<>();
    private final Map<Integer, UserDailyTasks> users = new ConcurrentHashMap<>();
    private final boolean persistent;

    public DailyTaskManager() {
        this(true);
        this.reload();
    }

    protected DailyTaskManager(boolean persistent) {
        this.persistent = persistent;
    }

    public synchronized void reload() {
        this.tasks.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM daily_tasks WHERE enabled = 1 ORDER BY is_bonus, sort_order, id");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                DailyTask task = DailyTask.fromResultSet(set);
                if (task.getGoalType() == null) {
                    LOGGER.warn("Daily task {} has an unknown goal type, skipped", task.getId());
                    continue;
                }
                this.register(task);
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the daily tasks", exception);
        }
        LOGGER.info("Daily Task Manager -> Loaded! ({} tasks)", this.tasks.size());
    }

    public synchronized void register(DailyTask task) {
        this.tasks.put(task.getId(), task);
    }

    public List<DailyTask> getTasks() {
        List<DailyTask> ordered = new ArrayList<>(this.tasks.values());
        ordered.sort(Comparator.comparing(DailyTask::isBonus)
                .thenComparingInt(DailyTask::getSortOrder)
                .thenComparingInt(DailyTask::getId));
        return Collections.unmodifiableList(ordered);
    }

    public DailyTask getTask(int id) {
        return this.tasks.get(id);
    }

    // ------------------------------------------------------------------ day helpers

    public static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Seconds until the next UTC midnight, when the tasks refresh. */
    public static int secondsLeftToday() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        return (int) Math.max(0, midnight.toEpochSecond() - now.toEpochSecond());
    }

    // ------------------------------------------------------------------ user state

    public UserDailyTasks stateFor(Habbo habbo) {
        return this.stateFor(habbo.getHabboInfo().getId());
    }

    public UserDailyTasks stateFor(int userId) {
        String day = today();
        UserDailyTasks state = this.users.get(userId);
        if (state == null || !state.getDay().equals(day)) {
            state = this.load(userId, day);
            this.users.put(userId, state);
        }
        return state;
    }

    public void unload(int userId) {
        this.users.remove(userId);
    }

    private UserDailyTasks load(int userId, String day) {
        UserDailyTasks state = new UserDailyTasks(userId, day);
        if (!this.persistent) {
            return state;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users_daily_tasks WHERE user_id = ? AND task_day = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, day);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    state.put(new UserDailyTaskState(
                            set.getInt("task_id"), day, set.getInt("repeats"), set.getInt("status")));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the daily tasks of user {}", userId, exception);
        }
        return state;
    }

    protected void save(int userId, UserDailyTaskState state) {
        if (!this.persistent) {
            return;
        }
        int repeats = state.getRepeats();
        int status = state.getStatus();
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_daily_tasks (user_id, task_id, task_day, repeats, status, updated_at)"
                                    + " VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE repeats = VALUES(repeats),"
                                    + " status = VALUES(status), updated_at = VALUES(updated_at)")) {
                statement.setInt(1, userId);
                statement.setInt(2, state.getTaskId());
                statement.setString(3, state.getDay());
                statement.setInt(4, repeats);
                statement.setInt(5, status);
                statement.setInt(6, Emulator.getIntUnixTimestamp());
                statement.execute();
            } catch (SQLException exception) {
                LOGGER.error("Could not save daily task {} of user {}", state.getTaskId(), userId, exception);
            }
        });
    }

    // ------------------------------------------------------------------ wire

    public ActiveDailyTasksComposer.Task toWire(DailyTask task, UserDailyTasks state) {
        UserDailyTaskState progress = state.get(task.getId());
        int repeats = progress == null ? 0 : Math.min(progress.getRepeats(), task.getRequiredRepeats());
        int status = progress == null ? UserDailyTaskState.STATUS_OPEN : progress.getStatus();
        return new ActiveDailyTasksComposer.Task(
                task.getId(),
                task.getCode(),
                task.getGoalType().actionType(),
                task.isBonus(),
                task.getImageVersion(),
                task.getCatalogName(),
                task.getRequiredRepeats(),
                repeats,
                status,
                secondsLeftToday(),
                List.of(new ActiveDailyTasksComposer.Reward(
                        0, task.getRewardType(), task.getRewardExtra(), task.getRewardAmount())));
    }

    public List<ActiveDailyTasksComposer.Task> activeTasks(Habbo habbo) {
        UserDailyTasks state = this.stateFor(habbo);
        List<ActiveDailyTasksComposer.Task> wire = new ArrayList<>();
        for (DailyTask task : this.getTasks()) {
            wire.add(this.toWire(task, state));
        }
        return wire;
    }

    public void sendActiveTasks(Habbo habbo) {
        habbo.getClient().sendResponse(new ActiveDailyTasksComposer(this.activeTasks(habbo)));
    }

    /** How many tasks are completed but not claimed yet (the unseen badge of the client). */
    public int claimableCount(Habbo habbo) {
        UserDailyTasks state = this.stateFor(habbo);
        int count = 0;
        for (DailyTask task : this.tasks.values()) {
            UserDailyTaskState progress = state.get(task.getId());
            if (progress != null && progress.isClaimable()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ actions

    public void progress(Habbo habbo, QuestGoalType goalType, int amount) {
        if (habbo == null || goalType == null || amount < 1 || this.tasks.isEmpty()) {
            return;
        }
        UserDailyTasks state = this.stateFor(habbo);
        for (DailyTask task : this.tasks.values()) {
            if (task.getGoalType() != goalType) {
                continue;
            }
            UserDailyTaskState progress = state.getOrCreate(task.getId());
            if (!progress.isOpen()) {
                continue;
            }
            progress.setRepeats(progress.getRepeats() + amount);
            boolean justCompleted = false;
            if (progress.getRepeats() >= task.getRequiredRepeats()) {
                progress.setRepeats(task.getRequiredRepeats());
                progress.setStatus(UserDailyTaskState.STATUS_COMPLETED);
                justCompleted = true;
            }
            this.save(state.getUserId(), progress);
            habbo.getClient()
                    .sendResponse(new DailyTaskUpdatedComposer(
                            task.getId(), progress.getRepeats(), progress.getStatus(), secondsLeftToday()));

            // The bonus task is the one the day builds up to, so its unlocking is announced rather
            // than left to a status change nobody watches: that is what the client celebrates.
            if (justCompleted && task.isBonus()) {
                habbo.getClient().sendResponse(new DailyTasksAddedComposer(List.of(this.toWire(task, state))));
            }
        }
    }

    /** ClaimDailyTask: pays the reward of a completed task; the client learns it from the status update. */
    public boolean claim(Habbo habbo, int taskId) {
        DailyTask task = this.tasks.get(taskId);
        if (task == null) {
            return false;
        }
        UserDailyTasks state = this.stateFor(habbo);
        UserDailyTaskState progress = state.get(taskId);
        if (progress == null || !progress.isClaimable()) {
            return false;
        }
        progress.setStatus(UserDailyTaskState.STATUS_CLAIMED);
        this.save(state.getUserId(), progress);
        QuestRewards.grantTyped(habbo, task.getRewardType(), task.getRewardExtra(), task.getRewardAmount());
        habbo.getClient()
                .sendResponse(new DailyTaskUpdatedComposer(
                        task.getId(), progress.getRepeats(), progress.getStatus(), secondsLeftToday()));
        if (!task.isBonus()) {
            QuestProgressEvents.dailyTaskClaimed(habbo);
        }
        return true;
    }
}
