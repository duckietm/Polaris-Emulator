package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.core.CryptoConfig;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.ServerMessage;
import polaris.plugin.PluginManager;

class RoomCycleFlushBatchTest {
    private Field runtimeField;
    private Field cryptoField;
    private Field pluginManagerField;
    private Object originalRuntime;
    private Object originalCrypto;
    private Object originalPluginManager;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        this.runtimeField = field("polarisRuntime");
        this.cryptoField = field("crypto");
        this.pluginManagerField = field("pluginManager");
        this.originalRuntime = this.runtimeField.get(null);
        this.originalCrypto = this.cryptoField.get(null);
        this.originalPluginManager = this.pluginManagerField.get(null);
        this.runtimeField.set(null, null);
        this.cryptoField.set(null, new CryptoConfig(false, "", "", ""));
        this.pluginManagerField.set(null, mock(PluginManager.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.channel != null) {
            this.channel.finishAndReleaseAll();
        }
        this.pluginManagerField.set(null, this.originalPluginManager);
        this.cryptoField.set(null, this.originalCrypto);
        this.runtimeField.set(null, this.originalRuntime);
    }

    @Test
    void cycleFlushesSequentialPacketWritesOnceAtItsBoundary() {
        FlushCountingHandler flushCounter = new FlushCountingHandler();
        this.channel = new EmbeddedChannel(flushCounter);
        GameClient client = new GameClient(this.channel);
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);
        Room room = new Room(41, 7) {
            @Override
            public void sendComposer(ServerMessage ignored) {
                client.sendResponse(first);
                client.sendResponse(second);
            }
        };
        room.scheduledComposers.add(new ServerMessage(102));

        new RoomCycleManager(room).cycle();

        assertEquals(1, flushCounter.flushes);
        assertSame(first, this.channel.readOutbound());
        assertSame(second, this.channel.readOutbound());
    }

    private static Field field(String name) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class FlushCountingHandler extends ChannelOutboundHandlerAdapter {
        private int flushes;

        @Override
        public void flush(ChannelHandlerContext context) throws Exception {
            this.flushes++;
            context.flush();
        }
    }
}
