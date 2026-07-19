package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameClientLookupTest {

    @Test
    void lookupUsesTheAuthenticatedClientIndex() {
        GameClientManager manager = new GameClientManager();
        GameClient client = clientFor(7);
        Habbo habbo = client.getHabbo();
        manager.claimAuthenticatedSession(7, client);

        assertSame(habbo, manager.getHabbo(7));
    }

    @Test
    void lookupFallsBackToRegisteredClientsAfterIndexRelease() {
        GameClientManager manager = new GameClientManager();
        GameClient client = clientFor(7);
        Habbo habbo = client.getHabbo();
        EmbeddedChannel channel = new EmbeddedChannel();
        manager.getSessions().put(channel.id(), client);
        manager.claimAuthenticatedSession(7, client);
        manager.releaseAuthenticatedSession(7, client);

        assertSame(habbo, manager.getHabbo(7));

        channel.finishAndReleaseAll();
    }

    @Test
    void lookupFallsBackWhileTheIndexedClientHasNoHabbo() {
        GameClientManager manager = new GameClientManager();
        GameClient indexedClient = mock(GameClient.class);
        GameClient teardownClient = clientFor(7);
        Habbo habbo = teardownClient.getHabbo();
        EmbeddedChannel channel = new EmbeddedChannel();
        manager.getSessions().put(channel.id(), teardownClient);
        manager.claimAuthenticatedSession(7, indexedClient);

        assertSame(habbo, manager.getHabbo(7));

        channel.finishAndReleaseAll();
    }

    private static GameClient clientFor(int userId) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(userId);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);
        return client;
    }
}
