package polaris.messages.incoming.guilds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.ClientMessage;
import polaris.plugin.PluginManager;
import polaris.plugin.events.guilds.GuildChangedBadgeEvent;
import polaris.threading.ThreadPooling;

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

        UserInfo userInfo = mock(UserInfo.class);
        when(userInfo.getId()).thenReturn(ownerId);

        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(userInfo);

        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

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
