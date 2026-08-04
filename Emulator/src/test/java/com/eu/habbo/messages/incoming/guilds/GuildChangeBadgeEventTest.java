package com.eu.habbo.messages.incoming.guilds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.guilds.GuildChangedBadgeEvent;
import com.eu.habbo.threading.ThreadPooling;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GuildChangeBadgeEventTest {

    @Test
    void changingBadgeRefreshesPlacedGuildFurniture() throws Exception {
        int guildId = 123;
        int roomId = 456;
        int ownerId = 789;

        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(guildId);
        when(guild.getRoomId()).thenReturn(roomId);
        when(guild.getOwnerId()).thenReturn(ownerId);
        when(guild.getBadge()).thenReturn("b000000");

        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);

        GuildManager guildManager = mock(GuildManager.class);
        when(guildManager.getGuild(guildId)).thenReturn(guild);

        RoomManager roomManager = mock(RoomManager.class);
        when(roomManager.getRoom(roomId)).thenReturn(room);

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getGuildManager()).thenReturn(guildManager);
        when(environment.getRoomManager()).thenReturn(roomManager);

        HabboInfo habboInfo = mock(HabboInfo.class);
        when(habboInfo.getId()).thenReturn(ownerId);

        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(habboInfo);

        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);

        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.fireEvent(any(GuildChangedBadgeEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getBoolean("imager.internal.enabled")).thenReturn(false);

        ThreadPooling threading = mock(ThreadPooling.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getPluginManager).thenReturn(pluginManager);
            emulator.when(Emulator::getConfig).thenReturn(configuration);
            emulator.when(Emulator::getThreading).thenReturn(threading);

            GuildChangeBadgeEvent event = new GuildChangeBadgeEvent();
            event.client = client;
            event.packet = badgePacket(guildId, 1, 2, 3);
            event.handle();
        }

        verify(room).refreshGuildColors(guild);
    }

    private static ClientMessage badgePacket(int guildId, int partId, int colorId, int position) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(guildId);
        buffer.writeInt(3);
        buffer.writeInt(partId);
        buffer.writeInt(colorId);
        buffer.writeInt(position);
        return new ClientMessage(0, buffer);
    }
}
