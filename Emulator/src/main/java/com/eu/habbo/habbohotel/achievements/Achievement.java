package com.eu.habbo.habbohotel.achievements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Achievement {

    public final int id;

    public final String name;

    public final AchievementCategories category;
    public volatile short state = 1;
    public volatile int displayMethod;
    public volatile String subcategory = "";

    /** Read-through view of the current level set; use {@link #levels()} for one consistent snapshot. */
    public final Map<Integer, AchievementLevel> levels = new LevelsView();

    // Immutable and swapped as a whole, so a reload never exposes a partial level set.
    private volatile Map<Integer, AchievementLevel> levelSnapshot = Map.of();

    public Achievement(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.category = AchievementCategories.valueOf(set.getString("category").toUpperCase());

        this.loadMetadata(set);

        this.addLevel(new AchievementLevel(set));
    }

    void loadMetadata(ResultSet set) throws SQLException {
        try {
            set.findColumn("state");
        } catch (SQLException missingMetadata) {
            return; // Legacy plugins may construct achievements from their original column projection.
        }
        this.state = set.getShort("state");
        this.displayMethod = set.getInt("display_method");
        this.subcategory = set.getString("subcategory");
    }

    /** The current level set, immutable and ordered by level. */
    public Map<Integer, AchievementLevel> levels() {
        return this.levelSnapshot;
    }

    public synchronized void addLevel(AchievementLevel level) {
        TreeMap<Integer, AchievementLevel> next = new TreeMap<>(this.levelSnapshot);
        next.put(level.level, level);
        this.levelSnapshot = Collections.unmodifiableMap(next);
    }

    /** Swaps in a complete level set at once. */
    public synchronized void replaceLevels(Collection<AchievementLevel> newLevels) {
        TreeMap<Integer, AchievementLevel> next = new TreeMap<>();
        for (AchievementLevel level : newLevels) next.put(level.level, level);
        this.levelSnapshot = Collections.unmodifiableMap(next);
    }

    /** Takes the levels and metadata of a freshly loaded copy of this achievement. */
    void refreshFrom(Achievement loaded) {
        this.state = loaded.state;
        this.displayMethod = loaded.displayMethod;
        this.subcategory = loaded.subcategory;
        this.replaceLevels(loaded.levels().values());
    }

    public AchievementLevel getLevelForProgress(int progress) {
        return levelForProgress(this.levelSnapshot, progress);
    }

    static AchievementLevel levelForProgress(Map<Integer, AchievementLevel> levels, int progress) {
        AchievementLevel l = null;
        if (progress > 0) {
            for (AchievementLevel level : levels.values()) {
                if (progress >= level.progress) {
                    if (l != null) {
                        if (l.level > level.level) {
                            continue;
                        }
                    }

                    l = level;
                }
            }
        }
        return l;
    }

    public AchievementLevel getNextLevel(int currentLevel) {
        return this.levelSnapshot.get(currentLevel + 1);
    }

    public AchievementLevel firstLevel() {
        return this.levelSnapshot.get(1);
    }

    public synchronized void clearLevels() {
        this.levelSnapshot = Map.of();
    }

    private final class LevelsView extends AbstractMap<Integer, AchievementLevel> {
        @Override
        public Set<Entry<Integer, AchievementLevel>> entrySet() {
            return Achievement.this.levelSnapshot.entrySet();
        }

        @Override
        public AchievementLevel get(Object key) {
            return Achievement.this.levelSnapshot.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return Achievement.this.levelSnapshot.containsKey(key);
        }

        @Override
        public int size() {
            return Achievement.this.levelSnapshot.size();
        }

        @Override
        public AchievementLevel put(Integer key, AchievementLevel value) {
            AchievementLevel previous = this.get(key);
            Achievement.this.addLevel(value);
            return previous;
        }

        @Override
        public void clear() {
            Achievement.this.clearLevels();
        }
    }
}
