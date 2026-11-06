package polaris.networking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ServerBindLifecycleTest {

    @Test
    void connectMarksServerListeningUntilStop() throws Exception {
        TestServer server = new TestServer("127.0.0.1", 0);
        server.initializePipeline();
        try {
            server.connect();
            assertTrue(server.isListening());
        } finally {
            server.stop();
        }

        assertFalse(server.isListening());
    }

    @Test
    void occupiedPortCausesUnsuccessfulStartup() throws Exception {
        String javaExecutable =
                Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(
                        javaExecutable,
                        "-cp",
                        System.getProperty("surefire.test.class.path"),
                        ServerBindFailureProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(finished, () -> "Bind-failure probe did not terminate:\n" + output);
        assertTrue(
                output.contains("ServerBindException"), () -> "A typed startup exception was not surfaced:\n" + output);
        assertNotEquals(0, process.exitValue(), () -> "Bind failure exited successfully:\n" + output);
    }

    private static final class TestServer extends Server {

        private TestServer(String host, int port) throws Exception {
            super("Test Server", host, port, 1, 1);
        }

        @Override
        public void initializePipeline() {
            super.initializePipeline();
            this.serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    // A pipeline is required to bind; handlers are not.
                }
            });
        }
    }
}
