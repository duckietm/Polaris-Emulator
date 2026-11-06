package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.quests.RewardTrack;
import polaris.hotel.quests.RewardTrackManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;

/**
 * Every gate between a firing progress box and a hotel-wide reward: the switch, the allow-list, the
 * room's enabler, the user being in the room, and the allowance per window.
 */
class WiredHotelProgressTest {

    private final AtomicLong now = new AtomicLong(1_000L);
    private final Map<Integer, Integer> progressed = new HashMap<>();
    private final WiredHotelProgress.AchievementProgressor progressor = (user, achievement, amount) ->
            this.progressed.merge(user.getUserInfo().getId(), amount, Integer::sum);

    private Room room;
    private WiredProgressLimiter limiter;

    @BeforeEach
    void setUp() {
        this.room = mock(Room.class);
        this.limiter = new WiredProgressLimiter(this.now::get, 1000);
    }

    private User user(int id, Room currentRoom, boolean inRoom) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(info.getId()).thenReturn(id);
        when(info.getCurrentRoom()).thenReturn(currentRoom);
        when(unit.isInRoom()).thenReturn(inRoom);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getRoomUnit()).thenReturn(unit);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        when(user.getUserStats()).thenReturn(mock(UserStats.class));
        when(this.room.getUser(unit)).thenReturn(user);
        return user;
    }

    private static WiredHotelProgressPolicy policy(String... allowed) {
        return new WiredHotelProgressPolicy(true, Set.of(allowed), 50, 3600);
    }

    private static Achievement achievement(String name) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getInt("id")).thenReturn(1);
        when(row.getString("name")).thenReturn(name);
        when(row.getString("category")).thenReturn("games");
        when(row.getInt("level")).thenReturn(1);
        when(row.findColumn("state")).thenThrow(new java.sql.SQLException("legacy"));
        return new Achievement(row);
    }

    private int progress(
            List<User> users,
            Achievement achievement,
            Set<String> declared,
            int mode,
            int amount,
            WiredHotelProgressPolicy policy) {
        return WiredHotelProgress.progressAchievement(
                this.room, users, achievement, declared, mode, amount, policy, this.limiter, this.progressor);
    }

    // ---------------------------------------------------------------- achievements

    @Test
    void anAllowedAndDeclaredAchievementProgressesTheUsersInTheRoom() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);

        assertEquals(5, this.progress(List.of(user), game, Set.of("ACH_Game"), 1, 5, policy("ACH_Game")));
        assertEquals(Map.of(7, 5), this.progressed);
    }

    @Test
    void anAmountOfZeroProgressesNothingAndALargeOneIsLeftToTheAllowance() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);

        assertEquals(0, this.progress(List.of(user), game, Set.of("ACH_Game"), 1, 0, policy("ACH_Game")));
        assertTrue(this.progressed.isEmpty());
        int given = this.progress(List.of(user), game, Set.of("ACH_Game"), 1, Integer.MAX_VALUE, policy("ACH_Game"));
        assertTrue(given > 0 && given < Integer.MAX_VALUE, "the hotel's window allowance bounds it");
    }

    @Test
    void theHotelSwitchOffMeansNothing() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy off = new WiredHotelProgressPolicy(false, Set.of("ACH_Game"), 50, 3600);

        assertEquals(0, this.progress(List.of(user), game, Set.of("ACH_Game"), 1, 5, off));
        assertTrue(this.progressed.isEmpty());
    }

    @Test
    void anAchievementOffTheAllowListIsRefusedEvenWhenTheRoomDeclaresIt() throws Exception {
        Achievement entry = achievement("ACH_RoomEntry");
        User user = this.user(7, this.room, true);

        assertEquals(0, this.progress(List.of(user), entry, Set.of("ACH_RoomEntry"), 1, 5, policy("ACH_Game")));
        assertTrue(this.progressed.isEmpty());
    }

    @Test
    void anAchievementNoEnablerInTheRoomNamesIsRefused() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);

        assertEquals(0, this.progress(List.of(user), game, Set.of("ACH_Other"), 1, 5, policy("ACH_Game")));
        assertEquals(0, this.progress(List.of(user), game, Set.of(), 1, 5, policy("ACH_Game")));
        assertTrue(this.progressed.isEmpty());
    }

    @Test
    void usersWhoLeftTheRoomOrAreElsewhereGetNothing() throws Exception {
        Achievement game = achievement("ACH_Game");
        User gone = this.user(7, mock(Room.class), true);
        User leaving = this.user(8, this.room, false);
        User here = this.user(9, this.room, true);

        assertEquals(
                1, this.progress(List.of(gone, leaving, here), game, Set.of("ACH_Game"), 1, 1, policy("ACH_Game")));
        assertEquals(Map.of(9, 1), this.progressed);
    }

    @Test
    void aRepeaterCannotGoPastTheAllowanceOfTheWindow() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy policy = new WiredHotelProgressPolicy(true, Set.of("ACH_Game"), 10, 60);

        int given = 0;
        for (int firing = 0; firing < 100; firing++) {
            given += this.progress(List.of(user), game, Set.of("ACH_Game"), 1, 3, policy);
        }
        assertEquals(10, given, "the cap of the window, however often it fires");

        this.now.addAndGet(60_000);
        assertEquals(3, this.progress(List.of(user), game, Set.of("ACH_Game"), 1, 3, policy));
    }

    @Test
    void raiseToOnlyAddsWhatIsMissing() throws Exception {
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);
        when(user.getUserStats().getAchievementProgress(game)).thenReturn(4);

        assertEquals(
                6,
                this.progress(
                        List.of(user),
                        game,
                        Set.of("ACH_Game"),
                        WiredHotelProgress.MODE_RAISE_TO,
                        10,
                        policy("ACH_Game")));
        when(user.getUserStats().getAchievementProgress(game)).thenReturn(12);
        assertEquals(
                0,
                this.progress(
                        List.of(user),
                        game,
                        Set.of("ACH_Game"),
                        WiredHotelProgress.MODE_RAISE_TO,
                        10,
                        policy("ACH_Game")));
    }

    @Test
    void oneFiringReachesAtMostFiftyUsers() throws Exception {
        Achievement game = achievement("ACH_Game");
        List<RoomUnit> units = new ArrayList<>();
        for (int id = 1; id <= 80; id++) {
            units.add(this.user(id, this.room, true).getRoomUnit());
        }

        List<User> users = WiredHotelProgress.presentUsers(this.room, units);
        assertEquals(WiredHotelProgressPolicy.MAX_USERS_PER_FIRING, users.size());
        this.progress(users, game, Set.of("ACH_Game"), 1, 1, policy("ACH_Game"));
        assertEquals(WiredHotelProgressPolicy.MAX_USERS_PER_FIRING, this.progressed.size());
    }

    @Test
    void presentUsersSkipsBotsAndStrangers() {
        User here = this.user(7, this.room, true);
        RoomUnit bot = mock(RoomUnit.class);

        assertEquals(
                List.of(here),
                WiredHotelProgress.presentUsers(this.room, List.of(bot, here.getRoomUnit(), here.getRoomUnit())));
    }

    @Test
    void amountsAndModesAreClamped() {
        assertEquals(1, WiredHotelProgress.normalizeAmount(-4));
        assertEquals(WiredHotelProgress.MAX_AMOUNT, WiredHotelProgress.normalizeAmount(Integer.MAX_VALUE));
        assertEquals(WiredHotelProgress.MODE_ADD, WiredHotelProgress.normalizeMode(9));
        assertEquals(WiredHotelProgress.MODE_RAISE_TO, WiredHotelProgress.normalizeMode(0));
    }

    @Test
    void achievementAmountsRunFromZeroToIntMaxAsAir() {
        assertEquals(0, WiredHotelProgress.normalizeAchievementAmount(-4));
        assertEquals(0, WiredHotelProgress.normalizeAchievementAmount(0));
        assertEquals(Integer.MAX_VALUE, WiredHotelProgress.normalizeAchievementAmount(Integer.MAX_VALUE));
    }

    // ---------------------------------------------------------------- reward tracks

    private static RewardTrackManager tracks(RewardTrack... registered) {
        RewardTrackManager manager = new RewardTrackManager(false) {};
        for (RewardTrack track : registered) {
            manager.register(track);
        }
        return manager;
    }

    private static RewardTrack season() {
        RewardTrack track = new RewardTrack("season_1", "blue", 1, 0, 0, false, 1, 0, 0, 0);
        RewardTrack.Task games = new RewardTrack.Task("games", "wired", "", false, 1);
        games.addLevel(new RewardTrack.Level(5, 10, false));
        games.addLevel(new RewardTrack.Level(10, 20, false));
        track.addTask(games);
        RewardTrack.Task talk = new RewardTrack.Task("talk", "chat_with_someone", "", false, 2);
        talk.addLevel(new RewardTrack.Level(5, 10, false));
        track.addTask(talk);
        RewardTrack.Task vip = new RewardTrack.Task("vip", "wired", "", true, 3);
        vip.addLevel(new RewardTrack.Level(1, 10, false));
        track.addTask(vip);
        return track;
    }

    @Test
    void anAllowedTrackTaskMovesAndPaysItsLevels() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);

        assertEquals(
                5,
                WiredHotelProgress.progressRewardTrack(
                        this.room,
                        List.of(user),
                        manager,
                        "season_1",
                        "games",
                        true,
                        5,
                        policy("season_1"),
                        this.limiter));
        assertEquals(5, manager.stateFor(user, track).progressOf("games"));
        assertEquals(10, manager.stateFor(user, track).getPoints());
    }

    @Test
    void aTrackOffTheAllowListOrSwitchedOffIsRefused() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy off = new WiredHotelProgressPolicy(false, Set.of("season_1"), 50, 3600);

        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room,
                        List.of(user),
                        manager,
                        "season_1",
                        "games",
                        true,
                        5,
                        policy("season_2"),
                        this.limiter));
        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "season_1", "games", true, 5, off, this.limiter));
        assertEquals(0, manager.stateFor(user, track).progressOf("games"));
    }

    @Test
    void aTaskEntryAllowsOnlyThatTask() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy policy = policy("season_1:games");

        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "season_1", "talk", true, 5, policy, this.limiter));
        assertEquals(
                2,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "season_1", "games", true, 2, policy, this.limiter));
    }

    @Test
    void anUnknownTaskAnInactiveTrackOrAPremiumTaskWithoutThePassDoNothing() {
        RewardTrack ended = new RewardTrack("old", "blue", 1, 0, 10, false, 1, 0, 0, 0);
        ended.addTask(new RewardTrack.Task("games", "wired", "", false, 1));
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track, ended);
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy policy = policy("season_1", "old");

        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "season_1", "nope", true, 5, policy, this.limiter));
        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "old", "games", true, 5, policy, this.limiter));
        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room, List.of(user), manager, "season_1", "vip", true, 5, policy, this.limiter));
        assertEquals(0, manager.stateFor(user, track).progressOf("vip"));
    }

    @Test
    void trackProgressStopsAtTheAllowanceAndSkipsUsersWhoLeft() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);
        User gone = this.user(8, null, false);
        WiredHotelProgressPolicy policy = new WiredHotelProgressPolicy(true, Set.of("season_1"), 7, 3600);

        int given = 0;
        for (int firing = 0; firing < 20; firing++) {
            given += WiredHotelProgress.progressRewardTrack(
                    this.room, List.of(user, gone), manager, "season_1", "games", true, 3, policy, this.limiter);
        }
        assertEquals(7, given);
        assertEquals(7, manager.stateFor(user, track).progressOf("games"));
        assertEquals(0, manager.stateFor(gone, track).progressOf("games"));
    }

    @Test
    void settingTheProgressCountsOnlyTheClimb() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);

        assertEquals(
                6,
                WiredHotelProgress.progressRewardTrack(
                        this.room,
                        List.of(user),
                        manager,
                        "season_1",
                        "games",
                        false,
                        6,
                        policy("season_1"),
                        this.limiter));
        assertEquals(
                0,
                WiredHotelProgress.progressRewardTrack(
                        this.room,
                        List.of(user),
                        manager,
                        "season_1",
                        "games",
                        false,
                        2,
                        policy("season_1"),
                        this.limiter));
        assertEquals(2, manager.stateFor(user, track).progressOf("games"), "setting lower is allowed");
        assertEquals(10, manager.stateFor(user, track).getPoints(), "and takes no points back");
    }

    @Test
    void aResetNeverPaysALevelTwice() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);
        WiredHotelProgressPolicy policy = policy("season_1");

        WiredHotelProgress.progressRewardTrack(
                this.room, List.of(user), manager, "season_1", "games", true, 5, policy, this.limiter);
        assertEquals(10, manager.stateFor(user, track).getPoints());

        assertEquals(1, WiredHotelProgress.resetRewardTrack(this.room, List.of(user), manager, "season_1", policy));
        assertEquals(0, manager.stateFor(user, track).progressOf("games"));
        assertEquals(10, manager.stateFor(user, track).getPoints(), "the points stay");

        WiredHotelProgress.progressRewardTrack(
                this.room, List.of(user), manager, "season_1", "games", true, 5, policy, this.limiter);
        assertEquals(10, manager.stateFor(user, track).getPoints(), "level 1 was paid already");

        WiredHotelProgress.progressRewardTrack(
                this.room, List.of(user), manager, "season_1", "games", true, 5, policy, this.limiter);
        assertEquals(30, manager.stateFor(user, track).getPoints(), "level 2 pays once it is reached");
    }

    @Test
    void aResetTouchesOnlyTheTasksTheHotelLetsWiredMove() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User user = this.user(7, this.room, true);
        manager.stateFor(user, track).setProgress("games", 3);
        manager.stateFor(user, track).setProgress("talk", 4);

        assertEquals(
                1,
                WiredHotelProgress.resetRewardTrack(
                        this.room, List.of(user), manager, "season_1", policy("season_1:games")));
        assertEquals(0, manager.stateFor(user, track).progressOf("games"));
        assertEquals(4, manager.stateFor(user, track).progressOf("talk"), "a task the hotel did not list stays");

        assertEquals(
                0,
                WiredHotelProgress.resetRewardTrack(this.room, List.of(user), manager, "season_1", policy("season_2")));
        assertEquals(
                0,
                WiredHotelProgress.resetRewardTrack(
                        this.room,
                        List.of(user),
                        manager,
                        "season_1",
                        new WiredHotelProgressPolicy(false, Set.of("season_1"), 50, 3600)));
        assertEquals(4, manager.stateFor(user, track).progressOf("talk"));
    }

    @Test
    void aResetSkipsUsersWhoLeft() {
        RewardTrack track = season();
        RewardTrackManager manager = tracks(track);
        User gone = this.user(8, mock(Room.class), true);
        manager.stateFor(gone, track).setProgress("games", 3);

        assertEquals(
                0,
                WiredHotelProgress.resetRewardTrack(this.room, List.of(gone), manager, "season_1", policy("season_1")));
        assertEquals(3, manager.stateFor(gone, track).progressOf("games"));
    }

    @Test
    void idsAreValidatedLikeTheStaffEditorDoes() {
        assertEquals("season_1", WiredHotelProgress.normalizeRewardTrackId(" season_1 "));
        assertEquals("a.b-c", WiredHotelProgress.normalizeRewardTrackId("a.b-c"));
        assertEquals("", WiredHotelProgress.normalizeRewardTrackId("season:1"));
        assertEquals("", WiredHotelProgress.normalizeRewardTrackId("a\tb"));
        assertEquals("", WiredHotelProgress.normalizeRewardTrackId("x".repeat(65)));
        assertEquals("", WiredHotelProgress.normalizeRewardTrackId(null));
    }

    @Test
    void theAchievementProgressorIsNeverCalledForARefusedFiring() throws Exception {
        WiredHotelProgress.AchievementProgressor refuse = mock(WiredHotelProgress.AchievementProgressor.class);
        Achievement game = achievement("ACH_Game");
        User user = this.user(7, this.room, true);

        WiredHotelProgress.progressAchievement(
                this.room, List.of(user), game, Set.of(), 1, 1, policy("ACH_Game"), this.limiter, refuse);
        org.mockito.Mockito.verify(refuse, org.mockito.Mockito.never())
                .progress(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
