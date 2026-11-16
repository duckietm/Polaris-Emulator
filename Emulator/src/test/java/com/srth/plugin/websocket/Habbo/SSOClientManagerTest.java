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
    void bindsWebsocketThatConnectsAfterGameSession() {
        HabboRpManager habboRpManager = new HabboRpManager();
        HabboRp habboRp = mock(HabboRp.class);
        Habbo habbo = habboWithSso("active-ticket");
        EmbeddedChannel websocketChannel = new EmbeddedChannel();

        when(habboRp.getHabbo()).thenReturn(habbo);
        habboRpManager.getHabboRps().put(7, habboRp);

        try {
            HabboClient roleplayClient =
                    SSOClientManager.bindToExistingSession("active-ticket", websocketChannel, habboRpManager);

            assertNotNull(roleplayClient);
            assertSame(habbo, roleplayClient.getHabbo());
            assertSame(websocketChannel, roleplayClient.getChannel());
        } finally {
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
