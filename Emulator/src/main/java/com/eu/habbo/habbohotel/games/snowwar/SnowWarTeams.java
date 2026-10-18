package com.eu.habbo.habbohotel.games.snowwar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Team split of a match. Players coming from a rematch keep the team they
 * had; everyone else goes to the smaller team (with nobody preferred that is
 * the old {@code index % teamCount} round robin). Then teams are balanced so
 * their sizes differ by at most one, moving the latest joiners first and a
 * rematcher only when nothing else can move.
 */
public final class SnowWarTeams {

    private SnowWarTeams() {}

    /** userId to teamId (0-based), in the order of {@code orderedUserIds}. */
    public static Map<Integer, Integer> assign(
            List<Integer> orderedUserIds, Map<Integer, Integer> preferredTeams, int teamCount) {
        Map<Integer, Integer> teams = new LinkedHashMap<>();
        if (teamCount <= 0) {
            return teams;
        }
        int[] sizes = new int[teamCount];
        List<Integer> unassigned = new ArrayList<>();

        for (Integer userId : orderedUserIds) {
            Integer preferred = preferredTeams == null ? null : preferredTeams.get(userId);
            if (preferred != null && preferred >= 0 && preferred < teamCount) {
                teams.put(userId, preferred);
                sizes[preferred]++;
            } else {
                teams.put(userId, -1);
                unassigned.add(userId);
            }
        }

        for (Integer userId : unassigned) {
            int team = smallest(sizes);
            teams.put(userId, team);
            sizes[team]++;
        }

        // Balance: move from the biggest to the smallest team until the sizes
        // differ by at most one.
        while (true) {
            int big = biggest(sizes);
            int small = smallest(sizes);
            if (sizes[big] - sizes[small] <= 1) {
                break;
            }
            Integer mover = lastOf(teams, big, unassigned);
            if (mover == null) {
                mover = lastOf(teams, big, teams.keySet());
            }
            teams.put(mover, small);
            sizes[big]--;
            sizes[small]++;
        }
        return teams;
    }

    private static Integer lastOf(Map<Integer, Integer> teams, int team, Collection<Integer> candidates) {
        Integer last = null;
        for (Map.Entry<Integer, Integer> entry : teams.entrySet()) {
            if (entry.getValue() == team && candidates.contains(entry.getKey())) {
                last = entry.getKey();
            }
        }
        return last;
    }

    private static int smallest(int[] sizes) {
        int best = 0;
        for (int i = 1; i < sizes.length; i++) {
            if (sizes[i] < sizes[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int biggest(int[] sizes) {
        int best = 0;
        for (int i = 1; i < sizes.length; i++) {
            if (sizes[i] > sizes[best]) {
                best = i;
            }
        }
        return best;
    }
}
