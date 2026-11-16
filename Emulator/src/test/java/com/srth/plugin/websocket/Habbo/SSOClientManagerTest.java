package com.srth.plugin.websocket.Habbo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class SSOClientManagerTest {

    @Test
    void bindsWebsocketThatConnectsBeforeGameSession() {
        Habbo habbo = habboWithSso("active-ticket");
        EmbeddedChannel websocketChannel = new EmbeddedChannel();

        try {
            SSOClientManager.addClient("active-ticket", websocketChannel);

            HabboClient roleplayClient = SSOClientManager.bindToSession(habbo);

            assertNotNull(roleplayClient);
            assertSame(habbo, roleplayClient.getHabbo());
            assertSame(websocketChannel, roleplayClient.getChannel());
        } finally {
            SSOClientManager.removeClient(websocketChannel);
            HabboClientManager.killClient(websocketChannel.id());
            websocketChannel.finishAndReleaseAll();
        }
    }

    @Test
    void disconnectRemovesOnlyTheSocketThatWasRegistered() {
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        EmbeddedChannel currentChannel = new EmbeddedChannel();

        try {
            SSOClientManager.addClient("active-ticket", oldChannel);
            SSOClientManager.addClient("active-ticket", currentChannel);
            SSOClientManager.removeClient(oldChannel);

            assertSame(currentChannel, SSOClientManager.getClientChannel("active-ticket"));
        } finally {
            SSOClientManager.removeClient(oldChannel);
            SSOClientManager.removeClient(currentChannel);
            oldChannel.finishAndReleaseAll();
            currentChannel.finishAndReleaseAll();
        }
    }

    @Test
    void bindsWebsocketThatConnectsAfterGameSession() {
        HabboRpManager habboRpManager = new HabboRpManager();
        HabboRp habboRp = mock(HabboRp.class);
        Habbo habbo = habboWithSso("active-ticket");
        EmbeddedChannel websocketChannel = new EmbeddedChannel();

        when(habboRp.getHabbo()).thenReturn(habbo);
        habboRpManager.getHabboRps().put(7, habboRp);

        try {
            SSOClientManager.addClient("active-ticket", websocketChannel);
            HabboClient roleplayClient =
                    SSOClientManager.bindToExistingSession("active-ticket", websocketChannel, habboRpManager);

            assertNotNull(roleplayClient);
            assertSame(habbo, roleplayClient.getHabbo());
            assertSame(websocketChannel, roleplayClient.getChannel());
        } finally {
            SSOClientManager.removeClient(websocketChannel);
            HabboClientManager.killClient(websocketChannel.id());
            websocketChannel.finishAndReleaseAll();
        }
    }

    @Test
    void reconnectReplacesThePreviousSocketForTheSameHabbo() {
        Habbo firstHabbo = habboWithId(7);
        Habbo reconnectedHabbo = habboWithId(7);
        EmbeddedChannel firstChannel = new EmbeddedChannel();
        EmbeddedChannel reconnectedChannel = new EmbeddedChannel();

        try {
            HabboClientManager manager = new HabboClientManager();
            manager.addClient(firstChannel, firstHabbo);
            manager.addClient(reconnectedChannel, reconnectedHabbo);

            assertSame(
                    reconnectedChannel,
                    HabboClientManager.getHabboClient(reconnectedHabbo).getChannel());
        } finally {
            HabboClientManager.killClient(firstChannel.id());
            HabboClientManager.killClient(reconnectedChannel.id());
            firstChannel.finishAndReleaseAll();
            reconnectedChannel.finishAndReleaseAll();
        }
    }

    private static Habbo habboWithId(int id) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static Habbo habboWithSso(String sso) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getSso()).thenReturn(sso);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }
}
