package com.eu.habbo.messages.outgoing.achievements.talenttrack;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import com.eu.habbo.habbohotel.achievements.TalentTrackLevel;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class TalentTrackComposer extends MessageComposer {
    public final Habbo habbo;
    public final TalentTrackType type;
    public TalentTrackComposer(Habbo habbo, TalentTrackType type) {
        this.habbo = habbo;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TalentTrackComposer);
        this.response.appendString(this.type.name().toLowerCase());

        LinkedHashMap<Integer, TalentTrackLevel> talentTrackLevels = Emulator.getGameEnvironment().getAchievementManager().getTalenTrackLevels(this.type);
        if (talentTrackLevels != null) {
            this.response.appendInt(talentTrackLevels.size()); //Count
            for (Map.Entry<Integer, TalentTrackLevel> set : talentTrackLevels.entrySet()) {
                try {
                    TalentTrackLevel level = set.getValue();

                    this.response.appendInt(level.level);

                    TalentTrackState state = TalentTrackState.LOCKED;

                    int currentLevel = this.habbo.getHabboStats().talentTrackLevel(this.type);

                    if (currentLevel + 1 == level.level) {
                        state = TalentTrackState.IN_PROGRESS;
                    } else if (currentLevel >= level.level) {
                        state = TalentTrackState.COMPLETED;
                    }

                    this.response.appendInt(state.id);
                    this.response.appendInt(level.achievements.size());

                    final TalentTrackState finalState = state;
                    for (Map.Entry<com.eu.habbo.habbohotel.achievements.Achievement, Integer> achievementEntry : level.achievements.entrySet()) {
                        com.eu.habbo.habbohotel.achievements.Achievement achievement = achievementEntry.getKey();
                        int index = achievementEntry.getValue();
                        if (achievement != null) {
                            this.response.appendInt(achievement.id);

                            //TODO Move this to TalenTrackLevel class
                            this.response.appendInt(index); //idk
                            this.response.appendString("ACH_" + achievement.name + index);

                            int progress = Math.max(0, this.habbo.getHabboStats().getAchievementProgress(achievement));
                            AchievementLevel achievementLevel = achievement.getLevelForProgress(progress);

                            if (achievementLevel == null) {
                                achievementLevel = achievement.firstLevel();
                            }
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

                    if (!level.items.isEmpty()) {
                        this.response.appendInt(level.items.size());
                        for (Item item : level.items) {
                            this.response.appendString(item.getName());
                            this.response.appendInt(0);
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

    public Habbo getHabbo() {
        return habbo;
    }

    public TalentTrackType getType() {
        return type;
    }
}
