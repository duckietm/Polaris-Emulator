package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

/**
 * AIR chatstyles 130-133 (wired_team_red, _blue, _yellow, _green) are system styles: a user in a Wired team talks,
 * shouts and whispers in their team's bubble, and nobody can pick one of them from the client.
 */
class RoomChatMessageWiredTeamBubbleTest {

    @Test
    void theTeamStylesAreRegisteredWithTheAirIds() {
        assertEquals(130, RoomChatMessageBubbles.getBubble(130).getType());
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_RED, RoomChatMessageBubbles.getBubble(130));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_BLUE, RoomChatMessageBubbles.getBubble(131));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_YELLOW, RoomChatMessageBubbles.getBubble(132));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_GREEN, RoomChatMessageBubbles.getBubble(133));
    }

    @Test
    void theTeamStylesAreNotOverriddenByTheUsersOwnChatColour() {
        for (int id = 130; id <= 133; id++) {
            assertFalse(RoomChatMessageBubbles.getBubble(id).isOverridable(), "style " + id);
            assertTrue(RoomChatMessageBubbles.isWiredTeamBubble(id));
        }
        assertFalse(RoomChatMessageBubbles.isWiredTeamBubble(129));
        assertFalse(RoomChatMessageBubbles.isWiredTeamBubble(134));
    }

    @Test
    void reloadingTheDynamicBubblesKeepsTheTeamStyles() {
        RoomChatMessageBubbles.removeDynamicBubbles();

        assertSame(RoomChatMessageBubbles.WIRED_TEAM_GREEN, RoomChatMessageBubbles.getBubble(133));
    }

    @Test
    void eachWiredTeamSpeaksInItsOwnBubble() {
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_RED, resolve(WiredGame.class, GameTeamColors.RED, 4));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_BLUE, resolve(WiredGame.class, GameTeamColors.BLUE, 4));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_YELLOW, resolve(WiredGame.class, GameTeamColors.YELLOW, 4));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_GREEN, resolve(WiredGame.class, GameTeamColors.GREEN, 4));
    }

    @Test
    void banzaiAndFreezeTeamsTalkInTheTeamBubbleAsOnAir() {
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_RED, resolve(BattleBanzaiGame.class, GameTeamColors.RED, 4));
        assertSame(RoomChatMessageBubbles.WIRED_TEAM_BLUE, resolve(FreezeGame.class, GameTeamColors.BLUE, 4));
    }

    @Test
    void aUserOutsideAnyTeamKeepsThePickedStyle() {
        assertEquals(23, resolve(null, null, 23).getType());
    }

    @Test
    void aClientCannotAskForATeamStyle() {
        assertSame(RoomChatMessageBubbles.NORMAL, resolve(null, null, 131));
        assertSame(RoomChatMessageBubbles.NORMAL, resolve(null, null, 130));
    }

    @Test
    void aWiredTeamWithoutAColourHasNoTeamBubble() {
        assertNull(RoomChatMessage.wiredTeamBubble(speaker(WiredGame.class, GameTeamColors.NONE)));
        assertNull(RoomChatMessage.wiredTeamBubble(null));
    }

    private static RoomChatMessageBubbles resolve(Class<? extends Game> game, GameTeamColors team, int requestedStyle) {
        return RoomChatMessage.resolveSpeakerBubble(
                RoomChatMessageBubbles.getBubble(requestedStyle), speaker(game, team));
    }

    private static Habbo speaker(Class<? extends Game> game, GameTeamColors team) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentGame()).thenAnswer(invocation -> game);
        if (team != null) {
            GamePlayer player = mock(GamePlayer.class);
            when(player.getTeamColor()).thenReturn(team);
            when(info.getGamePlayer()).thenReturn(player);
        }
        return habbo;
    }
}
