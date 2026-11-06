package polaris.messages.outgoing.achievements.talenttrack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementLevel;
import polaris.hotel.achievements.TalentTrackLevel;
import polaris.hotel.achievements.TalentTrackType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TalentTrackComposer extends MessageComposer {
    public final User user;
    public final TalentTrackType type;

    public TalentTrackComposer(User user, TalentTrackType type) {
        this.user = user;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TalentTrackComposer);
        this.response.appendString(this.type.name().toLowerCase());

        LinkedHashMap<Integer, TalentTrackLevel> talentTrackLevels =
                Emulator.getGameEnvironment().getAchievementManager().getTalenTrackLevels(this.type);
        if (talentTrackLevels != null) {
            this.response.appendInt(talentTrackLevels.size()); // Count
            for (Map.Entry<Integer, TalentTrackLevel> set : talentTrackLevels.entrySet()) {
                try {
                    TalentTrackLevel level = set.getValue();

                    this.response.appendInt(level.level);

                    TalentTrackState state = TalentTrackState.LOCKED;

                    int currentLevel = this.user.getUserStats().talentTrackLevel(this.type);

                    if (currentLevel + 1 == level.level) {
                        state = TalentTrackState.IN_PROGRESS;
                    } else if (currentLevel >= level.level) {
                        state = TalentTrackState.COMPLETED;
                    }

                    this.response.appendInt(state.id);
                    this.response.appendInt(level.achievements.size());

                    final TalentTrackState finalState = state;
                    for (Map.Entry<polaris.hotel.achievements.Achievement, Integer> achievementEntry :
                            level.achievements.entrySet()) {
                        polaris.hotel.achievements.Achievement achievement = achievementEntry.getKey();
                        int index = achievementEntry.getValue();
                        if (achievement != null) {
                            this.response.appendInt(achievement.id);

                            // TODO Move this to TalenTrackLevel class
                            this.response.appendInt(index); // idk
                            this.response.appendString("ACH_" + achievement.name + index);

                            int progress = Math.max(0, this.user.getUserStats().getAchievementProgress(achievement));
                            AchievementLevel achievementLevel = achievement.levels.get(index);
                            if (finalState != TalentTrackState.LOCKED) {
                                if (achievementLevel != null && achievementLevel.progress <= progress) {
                                    this.response.appendInt(2);
                                } else {
                                    this.response.appendInt(1);
                                }
                            } else {
                                this.response.appendInt(0);
                            }
                            this.response.appendInt(progress);
                            this.response.appendInt(achievementLevel != null ? achievementLevel.progress : 0);
                        } else {
                            this.response.appendInt(0);
                            this.response.appendInt(0);
                            this.response.appendString("");
                            this.response.appendInt(0);
                            this.response.appendInt(0);
                            this.response.appendInt(0);
                        }
                    }

                    if (level.perks != null && level.perks.length > 0) {
                        this.response.appendInt(level.perks.length);
                        for (String perk : level.perks) {
                            this.response.appendString(perk);
                        }
                    } else {
                        this.response.appendInt(-1);
                    }

                    if (!level.items.isEmpty() || level.hcDays > 0) {
                        this.response.appendInt(level.items.size() + (level.hcDays > 0 ? 1 : 0));
                        for (Item item : level.items) {
                            this.response.appendString(item.getName());
                            this.response.appendInt(0);
                        }
                        if (level.hcDays > 0) {
                            this.response.appendString("HABBO_CLUB");
                            this.response.appendInt(level.hcDays);
                        }
                    } else {
                        this.response.appendInt(-1);
                    }
                } catch (NoSuchElementException e) {
                    return null;
                }
            }
        } else {
            this.response.appendInt(0);
        }
        return this.response;
    }

    public enum TalentTrackState {
        LOCKED(0),
        IN_PROGRESS(1),
        COMPLETED(2);

        public final int id;

        TalentTrackState(int id) {
            this.id = id;
        }
    }

    public User getUser() {
        return user;
    }

    public TalentTrackType getType() {
        return type;
    }
}
