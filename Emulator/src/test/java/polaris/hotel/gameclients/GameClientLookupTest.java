package polaris.hotel.gameclients;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

class GameClientLookupTest {

    @Test
    void lookupUsesTheAuthenticatedClientIndex() {
        GameClientManager manager = new GameClientManager();
        GameClient client = clientFor(7);
        User user = client.getUser();
        manager.claimAuthenticatedSession(7, client);

        assertSame(user, manager.getUser(7));
    }

    @Test
    void lookupFallsBackToRegisteredClientsAfterIndexRelease() {
        GameClientManager manager = new GameClientManager();
        GameClient client = clientFor(7);
        User user = client.getUser();
        EmbeddedChannel channel = new EmbeddedChannel();
        manager.getSessions().put(channel.id(), client);
        manager.claimAuthenticatedSession(7, client);
        manager.releaseAuthenticatedSession(7, client);

        assertSame(user, manager.getUser(7));

        channel.finishAndReleaseAll();
    }

    @Test
    void lookupFallsBackWhileTheIndexedClientHasNoHabbo() {
        GameClientManager manager = new GameClientManager();
        GameClient indexedClient = mock(GameClient.class);
        GameClient teardownClient = clientFor(7);
        User user = teardownClient.getUser();
        EmbeddedChannel channel = new EmbeddedChannel();
        manager.getSessions().put(channel.id(), teardownClient);
        manager.claimAuthenticatedSession(7, indexedClient);

        assertSame(user, manager.getUser(7));

        channel.finishAndReleaseAll();
    }

    private static GameClient clientFor(int userId) {
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(userId);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);
        return client;
    }
}
