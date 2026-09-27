package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The official Introduction track: its seeded tasks, their action types and the ordered saves. */
class IntroductionRewardTrackTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260927120000__reward_track_introduction.sql");

    /** reward_track.introduction.task.&lt;id&gt; in the official client texts, with the official action. */
    private static final Map<String, String> OFFICIAL_TASKS = new LinkedHashMap<>();

    static {
        String[][] tasks = {
            {"chat_with_users", "chat_with_someone"},
            {"visit_rooms", "enter_other_users_room"},
            {"place_furniture", "place_item"},
            {"give_respect", "give_respect"},
            {"buy_catalog_furni", "buy_from_catalogue"},
            {"change_motto", "change_motto"},
            {"change_outfit", "change_figure"},
            {"close_love_lock", "friend_furni_locked"},
            {"create_room", "create_room"},
            {"dance_in_room", "dance"},
            {"feed_pet", "pet_eat"},
            {"follow_friend", "follow_friend"},
            {"go_swimming", "swim"},
            {"grab_drink", "find_hand_item"},
            {"level_pet", "pet_level"},
            {"make_friends", "request_friend"},
            {"move_furniture", "move_item"},
            {"pet_a_pet", "pet_respect"},
            {"place_builders_club_furni", "place_builders_club_furni"},
            {"publish_picture", "publish_picture"},
            {"replenish_respect", "replenish_respect"},
            {"rotate_furniture", "rotate_item"},
            {"send_messenger_invite", "send_messenger_invite"},
            {"send_messenger_message", "send_messenger_message"},
            {"set_relationship_status", "set_relationship_status"},
            {"use_furniture", "switch_item_state"},
            {"use_habbicon", "use_habbicon"},
            {"use_teleport", "teleport"},
            {"wave_at_user", "wave"},
            {"wear_badge", "wear_badge"},
        };
        for (String[] task : tasks) {
            OFFICIAL_TASKS.put(task[0], task[1]);
        }
    }

    @Test
    void everyOfficialActionIsAPlayGoalWithItsOwnIcon() {
        Set<QuestGoalType> goals = new HashSet<>();
        for (String action : OFFICIAL_TASKS.values()) {
            QuestGoalType goal = QuestGoalType.fromCode(action);
            assertNotNull(goal, action);
            assertEquals(action, goal.actionType(), "the wire keeps the official icon name");
            assertTrue(goal != QuestGoalType.WIRED);
            goals.add(goal);
        }
        assertEquals(30, goals.size(), "no two official tasks share a goal");
        assertEquals(QuestGoalType.SWIM, QuestGoalType.fromCode("SWIM"), "the enum name is still accepted");
    }

    @Test
    void theMigrationSeedsEveryOfficialTaskWithLevels() throws Exception {
        String sql = Files.readString(MIGRATION);
        Matcher task = Pattern.compile("\\('introduction','([a-z_]+)','([a-z_]+)','',0,\\d+\\)")
                .matcher(sql);
        Map<String, String> seeded = new LinkedHashMap<>();
        while (task.find()) {
            seeded.put(task.group(1), task.group(2));
        }
        assertEquals(OFFICIAL_TASKS, seeded);
        for (String id : OFFICIAL_TASKS.keySet()) {
            assertTrue(sql.contains("('introduction','" + id + "',1,"), id + " has a first level");
        }
        assertTrue(sql.contains("INSERT IGNORE INTO reward_tracks"), "re-running never rewrites the track");
    }

    @Test
    void anOfficialTaskMovesOnlyWithItsOwnAction() {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        RewardTrack track = new RewardTrack("introduction", "blue", 0, 0, 0, false, 1, 0, 0, 0);
        RewardTrack.Task swim = new RewardTrack.Task("go_swimming", "swim", "", false, 1);
        swim.addLevel(new RewardTrack.Level(1, 10, false));
        track.addTask(swim);
        manager.register(track);
        Habbo habbo = habbo(9);

        manager.progress(habbo, QuestGoalType.DANCE, 1);
        assertEquals(0, manager.stateFor(habbo, track).getPoints());
        manager.progress(habbo, QuestGoalType.SWIM, 1);
        assertEquals(10, manager.stateFor(habbo, track).getPoints());
    }

    @Test
    void aSlowOlderSaveNeverOverwritesNewerPoints() {
        List<Runnable> queued = new ArrayList<>();
        List<int[]> written = new ArrayList<>();
        RewardTrackManager manager = new RewardTrackManager(true) {
            @Override
            protected void runPersistence(Runnable write) {
                queued.add(write);
            }

            @Override
            protected void writeTrack(int userId, String trackId, int points, boolean premium) {
                written.add(new int[] {points, premium ? 1 : 0});
            }

            @Override
            protected void writeTaskProgress(int userId, String trackId, String taskId, int count, int peak) {
                written.add(new int[] {count, peak});
            }
        };
        UserRewardTrackState state = new UserRewardTrackState("introduction", 0, false);

        state.setPremium(true);
        state.addPoints(25);
        manager.saveTrack(1, state); // the premium purchase
        state.addPoints(10);
        manager.saveTrack(1, state); // gameplay right after it

        Collections.reverse(queued); // the older save runs last
        queued.forEach(Runnable::run);
        int[] last = written.get(written.size() - 1);
        assertEquals(35, last[0], "the final row holds the newest points");
        assertEquals(1, last[1], "and keeps premium");

        written.clear();
        queued.clear();
        state.setProgress("swim", 1);
        manager.saveTaskProgress(1, state, "swim");
        state.setProgress("swim", 2);
        manager.saveTaskProgress(1, state, "swim");
        Collections.reverse(queued);
        queued.forEach(Runnable::run);
        assertEquals(2, written.get(written.size() - 1)[0]);
    }

    private static Habbo habbo(int userId) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(userId);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        return habbo;
    }
}
