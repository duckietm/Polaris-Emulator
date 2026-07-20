package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class RoomStateOwnershipTest {

    @Test
    void roomDisposalDisposesGamesOwnedByThePublicGameManager() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = new Room(41, 7, new RoomDependencies(dataSource::getConnection));
        Game game = mock(Game.class);
        room.addGame(game);
        setField(room, "roomSpecialTypes", new RoomSpecialTypes());

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            new RoomDisposer(room).dispose(true);
        }

        verify(game).dispose();
        assertTrue(room.getGames().isEmpty());
    }

    @Test
    void roomPromotionAccessorsObserveThePromotionManagerState() throws Exception {
        Room room = new Room(41, 7);
        RoomPromotion promotion = mock(RoomPromotion.class);
        when(promotion.getDescription()).thenReturn("Promotion description");
        when(promotion.getEndTimestamp()).thenReturn(Emulator.getIntUnixTimestamp() + 60);
        setField(room.getPromotionManager(), "promotion", promotion);
        setField(room.getPromotionManager(), "promoted", true);

        assertSame(promotion, room.getPromotion());
        assertTrue(room.isPromoted());
    }

    @Test
    void wordQuizManagerUsesTheLegacyPluginVisibleRoomState() {
        Room room = new Room(41, 7);

        room.startWordQuiz("Question", 60_000);

        assertSame(room.userVotes, room.getWordQuizManager().getUserVotes());
        assertTrue(room.wordQuiz.equals("Question"));
        assertTrue(room.wordQuizEnd > Emulator.getIntUnixTimestamp());
    }

    @Test
    void mediaCollectionsRemainStableLiveViews() {
        Room room = new Room(41, 7);

        assertSame(room.getYoutubePlaylist(), room.getYoutubePlaylist());
        assertSame(room.getYoutubeWatchers(), room.getYoutubeWatchers());

        room.getYoutubePlaylist().add("video");
        room.getYoutubeWatchers().add(12);
        assertTrue(room.getYoutubePlaylist().contains("video"));
        assertTrue(room.getYoutubeWatchers().contains(12));
    }

    @Test
    void roomAndRightsManagerShareTheSameBanState() {
        Room room = new Room(41, 7);
        RoomBan ban = new RoomBan(41, 12, "banned", Emulator.getIntUnixTimestamp() + 60);

        room.addRoomBan(ban);

        assertSame(room.getBannedHabbos(), room.getRightsManager().getBannedHabbos());
        assertSame(ban, room.getBannedHabbos().get(12));
    }

    @Test
    void roomManagersShareOneMuteState() {
        Room room = new Room(41, 7);

        assertSame(
                room.getChatManager().getMutedHabbos(), room.getRightsManager().getMutedHabbos());
    }

    @Test
    void legacyRightsListAndManagerSetAreLiveViewsOfOneState() {
        Room room = new Room(41, 7);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(12);

        room.getRights().add(12);
        room.getRights().add(12);

        assertTrue(room.hasRights(habbo));
        assertTrue(room.getRightsManager().getRights().contains(12));

        room.getRightsManager().getRights().add(20);
        assertTrue(room.getRights().contains(20));

        room.getRightsManager().getRights().remove(12);
        assertFalse(room.getRights().contains(12));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
