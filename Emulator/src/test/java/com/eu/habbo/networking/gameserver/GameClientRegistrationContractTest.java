package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * GameMessageRateLimit drops packets of a channel without a game client. The client must therefore be
 * attached on the event loop at registration, not by GameMessageHandler on the packet executor, where
 * the registration waits behind other work under load and a client's first packets got lost.
 */
class GameClientRegistrationContractTest {
    @Test
    void bothPipelinesAttachTheClientBeforeTheRateLimit() throws Exception {
        for (String file : new String[] {"WebSocketChannelInitializer.java", "GameServer.java"}) {
            String source = Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/" + file));
            int registrar = source.indexOf("new GameMessageHandler.ClientRegistrar()");
            int rateLimit = source.indexOf("new GameMessageRateLimit()");
            assertTrue(registrar > -1 && registrar < rateLimit, file + " registers the client before the rate limit");
            assertTrue(
                    source.indexOf("\"gameClientRegistrar\", new GameMessageHandler.ClientRegistrar())") > -1,
                    file + " adds the registrar without an executor group");
        }
    }

    @Test
    void theHandlerOnThePacketExecutorKeepsAnAttachedClient() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/decoders/GameMessageHandler.java"));
        String compact = source.replaceAll("\\s+", "");
        assertTrue(
                compact.contains(
                        "if(ctx.channel().attr(GameServerAttributes.CLIENT).get()!=null){ctx.fireChannelRegistered();return;}"));
    }
}
