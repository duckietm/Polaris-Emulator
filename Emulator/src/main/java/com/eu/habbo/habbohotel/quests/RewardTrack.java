package com.eu.habbo.habbohotel.quests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A reward track definition: `reward_tracks` with its tasks, levels and prizes. */
public final class RewardTrack {
    /** One level of a task: reaching requiredCount pays pointsReward. */
    public record Level(int requiredCount, int pointsReward, boolean premium) {}

    /** A task of the track; `actionType` is the official client action name that picks the icon. */
    public static final class Task {
        private final String id;
        private final String actionType;
        private final QuestGoalType goalType;
        private final String parameter;
        private final boolean premium;
        private final int sortOrder;
        private final List<Level> levels = new ArrayList<>();

        public Task(String id, String actionType, String parameter, boolean premium, int sortOrder) {
            this.id = id;
            this.actionType = actionType;
            this.goalType = QuestGoalType.fromCode(actionType);
            this.parameter = parameter == null ? "" : parameter;
            this.premium = premium;
            this.sortOrder = sortOrder;
        }

        public String getId() {
            return this.id;
        }

        public String getActionType() {
            return this.actionType;
        }

        public QuestGoalType getGoalType() {
            return this.goalType;
        }

        public String getParameter() {
            return this.parameter;
        }

        public boolean isPremium() {
            return this.premium;
        }

        public int getSortOrder() {
            return this.sortOrder;
        }

        public List<Level> getLevels() {
            return Collections.unmodifiableList(this.levels);
        }

        public void addLevel(Level level) {
            this.levels.add(level);
            this.levels.sort(Comparator.comparingInt(Level::requiredCount));
        }

        public boolean isComplete(int progressCount) {
            for (Level level : this.levels) {
                if (progressCount < level.requiredCount()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A prize on the track, claimable once `requiredPoints` are collected. */
    public static final class Prize {
        private final String id;
        private final int requiredPoints;
        private final int productItemTypeId;
        private final String rewardType;
        private final String extraParams;
        private final int rewardAmount;
        private final boolean premium;
        private final int sortOrder;

        public Prize(
                String id,
                int requiredPoints,
                int productItemTypeId,
                String rewardType,
                String extraParams,
                int rewardAmount,
                boolean premium,
                int sortOrder) {
            this.id = id;
            this.requiredPoints = requiredPoints;
            this.productItemTypeId = productItemTypeId;
            this.rewardType = rewardType == null ? QuestRewards.TYPE_DUCKETS : rewardType;
            this.extraParams = extraParams == null ? "" : extraParams;
            this.rewardAmount = rewardAmount;
            this.premium = premium;
            this.sortOrder = sortOrder;
        }

        public String getId() {
            return this.id;
        }

        public int getRequiredPoints() {
            return this.requiredPoints;
        }

        public int getProductItemTypeId() {
            return this.productItemTypeId;
        }

        public String getRewardType() {
            return this.rewardType;
        }

        public String getExtraParams() {
            return this.extraParams;
        }

        public int getRewardAmount() {
            return this.rewardAmount;
        }

        public boolean isPremium() {
            return this.premium;
        }

        public int getSortOrder() {
            return this.sortOrder;
        }
    }

    private final String id;
    private final String theme;
    private final int sortOrder;
    private final int startsAt;
    private final int endsAt;
    private final boolean hasPremium;
    private final double premiumTaskPointsBoost;
    private final int premiumInstantPoints;
    private final int premiumCostDiamonds;
    private final int premiumCostCredits;
    private final List<Task> tasks = new ArrayList<>();
    private final List<Prize> prizes = new ArrayList<>();

    public RewardTrack(
            String id,
            String theme,
            int sortOrder,
            int startsAt,
            int endsAt,
            boolean hasPremium,
            double premiumTaskPointsBoost,
            int premiumInstantPoints,
            int premiumCostDiamonds,
            int premiumCostCredits) {
        this.id = id;
        this.theme = theme == null || theme.isBlank() ? "blue" : theme;
        this.sortOrder = sortOrder;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.hasPremium = hasPremium;
        this.premiumTaskPointsBoost = premiumTaskPointsBoost;
        this.premiumInstantPoints = premiumInstantPoints;
        this.premiumCostDiamonds = premiumCostDiamonds;
        this.premiumCostCredits = premiumCostCredits;
    }

    public String getId() {
        return this.id;
    }

    public String getTheme() {
        return this.theme;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public int getStartsAt() {
        return this.startsAt;
    }

    public int getEndsAt() {
        return this.endsAt;
    }

    public boolean isActive(int now) {
        return (this.startsAt <= 0 || this.startsAt <= now) && (this.endsAt <= 0 || now < this.endsAt);
    }

    public boolean hasPremium() {
        return this.hasPremium;
    }

    /** A multiplier: 1.5 means premium users earn 50% more task points. */
    public double getPremiumTaskPointsBoost() {
        return this.premiumTaskPointsBoost;
    }

    public int getPremiumInstantPoints() {
        return this.premiumInstantPoints;
    }

    public int getPremiumCostDiamonds() {
        return this.premiumCostDiamonds;
    }

    public int getPremiumCostCredits() {
        return this.premiumCostCredits;
    }

    public List<Task> getTasks() {
        return Collections.unmodifiableList(this.tasks);
    }

    public List<Prize> getPrizes() {
        return Collections.unmodifiableList(this.prizes);
    }

    public Task getTask(String taskId) {
        for (Task task : this.tasks) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    public Prize getPrize(String prizeId) {
        for (Prize prize : this.prizes) {
            if (prize.getId().equals(prizeId)) {
                return prize;
            }
        }
        return null;
    }

    public void addTask(Task task) {
        this.tasks.add(task);
        this.tasks.sort(Comparator.comparingInt(Task::getSortOrder).thenComparing(Task::getId));
    }

    public void addPrize(Prize prize) {
        this.prizes.add(prize);
        this.prizes.sort(Comparator.comparingInt(Prize::getRequiredPoints)
                .thenComparingInt(Prize::getSortOrder)
                .thenComparing(Prize::getId));
    }

    /** Points a task pays when its progress moves from `before` to `after`, with the premium boost applied. */
    public int pointsFor(Task task, int before, int after, boolean premiumUser) {
        int points = 0;
        for (Level level : task.getLevels()) {
            if (level.premium() && !premiumUser) {
                continue;
            }
            if (before < level.requiredCount() && after >= level.requiredCount()) {
                points += level.pointsReward();
            }
        }
        if (premiumUser && this.premiumTaskPointsBoost > 1) {
            points = (int) Math.round(points * this.premiumTaskPointsBoost);
        }
        return points;
    }
}
