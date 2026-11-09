package polaris.messages.incoming.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

/** A post needs a forum that is switched on and that the poster may read. */
class GuildForumPostThreadEventTest {
    private static final int GUILD_ID = 7;

    private MockedStatic<Emulator> emulator;
    private MockedStatic<ForumThread> threads;
    private final GameClient client = mock(GameClient.class);
    private final Guild guild = mock(Guild.class);

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(this.client.getUser()).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(5);

        GuildManager guilds = mock(GuildManager.class);
        when(guilds.getGuild(GUILD_ID)).thenReturn(this.guild);
        WordFilter filter = mock(WordFilter.class);
        when(filter.filter(anyString(), any())).thenAnswer(i -> i.getArgument(0));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getGuildManager()).thenReturn(guilds);
        when(environment.getWordFilter()).thenReturn(filter);
        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

        when(this.guild.hasForum()).thenReturn(true);
        when(this.guild.canUserReadForum(anyInt(), any(), anyBoolean())).thenReturn(true);
        this.threads = mockStatic(ForumThread.class);
    }

    @AfterEach
    void tearDown() {
        this.threads.close();
        this.emulator.close();
    }

    @Test
    void aForumThatIsSwitchedOffTakesNoThread() throws Exception {
        when(this.guild.hasForum()).thenReturn(false);

        this.assertRefused();
    }

    @Test
    void someoneWhoCannotReadTheForumCannotPostInIt() throws Exception {
        when(this.guild.canUserReadForum(anyInt(), any(), anyBoolean())).thenReturn(false);

        this.assertRefused();
    }

    private void assertRefused() throws Exception {
        List<MessageComposer> sent = this.post();

        assertEquals(1, sent.size());
        assertEquals(ConnectionErrorComposer.class, sent.get(0).getClass());
        this.threads.verify(() -> ForumThread.create(any(), any(), anyString(), anyString()), never());
    }

    private List<MessageComposer> post() throws Exception {
        var buffer = Unpooled.buffer();
        buffer.writeInt(GUILD_ID);
        buffer.writeInt(0);
        writeString(buffer, "A subject long enough");
        writeString(buffer, "A message long enough to post");
        GuildForumPostThreadEvent handler = new GuildForumPostThreadEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.GuildForumPostThreadEvent, buffer);

        handler.handle();

        ArgumentCaptor<MessageComposer> composers = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.client, atLeast(0)).sendResponse(composers.capture());
        return composers.getAllValues();
    }

    private static void writeString(io.netty.buffer.ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }
}
