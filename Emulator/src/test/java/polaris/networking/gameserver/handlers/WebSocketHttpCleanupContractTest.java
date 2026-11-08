package polaris.networking.gameserver.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every HTTP-only handler the initializer puts ahead of the WebSocket protocol handler must go once the channel has
 * upgraded: the wired API pair was missing, so every game frame still hopped onto the blocking HTTP executor.
 */
class WebSocketHttpCleanupContractTest {
    /**
     * The handlers the upgrade itself needs, the ones Netty's protocol handler takes out, and the ones that serve the
     * whole connection (logging, outbound backpressure).
     */
    private static final Set<String> KEPT =
            Set.of("logger", "outboundBackpressure", "httpCodec", "httpAggregator", "wsHttpHandler");

    @Test
    void everyHttpOnlyHandlerIsRemovedAfterTheUpgrade() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/networking/gameserver/WebSocketChannelInitializer.java"));
        String beforeUpgrade = source.substring(0, source.indexOf("\"wsProtocolHandler\""));

        List<String> added = new ArrayList<>();
        Matcher matcher = Pattern.compile("addLast\\(\\s*(?:blockingHttp,\\s*)?\"([A-Za-z]+)\"")
                .matcher(beforeUpgrade);
        while (matcher.find()) {
            added.add(matcher.group(1));
        }
        assertTrue(added.contains("wiredApiHandler"), "the scan must see the initializer's handlers");

        List<String> missing = new ArrayList<>();
        for (String name : added) {
            if (!KEPT.contains(name)
                    && !List.of(WebSocketHttpCleanupHandler.HTTP_ONLY_HANDLERS).contains(name)) {
                missing.add(name);
            }
        }
        assertEquals(List.of(), missing);
    }
}
