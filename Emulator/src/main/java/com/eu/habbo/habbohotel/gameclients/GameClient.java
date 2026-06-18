package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.crypto.HabboEncryption;
import com.eu.habbo.habbohotel.LatencyTracker;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.plugin.events.emulator.OutgoingPacketEvent;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameClient.class);

    private final Channel channel;
    private final HabboEncryption encryption;
	private final LatencyTracker latencyTracker;

    private Habbo habbo;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private boolean handshakeFinished;
    private String machineId = "";
    private String ssoTicket = "";

    public final ConcurrentHashMap<Integer, Integer> incomingPacketCounter = new ConcurrentHashMap<>(25);
    public final ConcurrentHashMap<Class<? extends MessageHandler>, Long> messageTimestamps = new ConcurrentHashMap<>();
    public long lastPacketCounterCleared = Emulator.getIntUnixTimestamp();

    public GameClient(Channel channel) {
        this.channel = channel;
        this.encryption = Emulator.getCrypto().isEnabled()
                ? new HabboEncryption(
                    Emulator.getCrypto().getExponent(),
                    Emulator.getCrypto().getModulus(),
                    Emulator.getCrypto().getPrivateExponent())
                : null;
			this.latencyTracker = new LatencyTracker();
    }

    public Channel getChannel() {
        return this.channel;
    }

    public HabboEncryption getEncryption() {
        return encryption;
    }
	
	public LatencyTracker getLatencyTracker() { 
		return latencyTracker;
	}

    public Habbo getHabbo() {
        return this.habbo;
    }

    public void setHabbo(Habbo habbo) {
        this.habbo = habbo;
    }

    public boolean isHandshakeFinished() {
        return handshakeFinished;
    }

    public void setHandshakeFinished(boolean handshakeFinished) {
        this.handshakeFinished = handshakeFinished;
    }

    public String getMachineId() {
        return this.machineId;
    }

    public void setMachineId(String machineId) {
        if (machineId == null) {
            throw new RuntimeException("Cannot set machineID to NULL");
        }

        this.machineId = machineId;
    }

    public String getSsoTicket() {
        return this.ssoTicket;
    }

    public void setSsoTicket(String ssoTicket) {
        this.ssoTicket = ssoTicket != null ? ssoTicket : "";
    }

    public void sendResponse(MessageComposer composer) {
        this.sendResponse(composer.compose());
    }

    public void sendResponse(ServerMessage response) {
        if (this.channel.isOpen()) {
            if (response == null || response.getHeader() <= 0) {
                return;
            }

            OutgoingPacketEvent event = new OutgoingPacketEvent(this.habbo, response.getComposer(), response);
            Emulator.getPluginManager().fireEvent(event);

            if (event.isCancelled()) {
                return;
            }

            if (event.hasCustomMessage()) {
                response = event.getCustomMessage();
            }

            this.channel.write(response, this.channel.voidPromise());
            this.channel.flush();
        }
    }

    public void sendResponses(ArrayList<ServerMessage> responses) {
        if (this.channel.isOpen()) {
            for (ServerMessage response : responses) {
                if (response == null || response.getHeader() <= 0) {
                    return;
                }

                OutgoingPacketEvent event = new OutgoingPacketEvent(this.habbo, response.getComposer(), response);
                Emulator.getPluginManager().fireEvent(event);

                if (event.isCancelled()) {
                    continue;
                }

                if (event.hasCustomMessage()) {
                    response = event.getCustomMessage();
                }

                this.channel.write(response);
            }

            this.channel.flush();
        }
    }
	
	public void sendKeepAlive() {
        if (this.channel != null && this.channel.isOpen()) {
            this.channel.writeAndFlush(new ServerMessage(-1));
        }
    }

    public void dispose() {
        this.dispose(true);
    }

    public void dispose(boolean allowSessionResume) {
        if (!this.disposed.compareAndSet(false, true)) {
            return;
        }

        try {
            this.channel.close();

            if (this.habbo != null) {
                // Agisci sull'Habbo SOLO se è ancora attaccato a QUESTO client. Su un
                // reconnect veloce (drop Cloudflare → il client riconnette) l'Habbo può
                // essere già stato riassegnato alla NUOVA connessione (session resume):
                // in quel caso questo dispose della vecchia connessione NON deve
                // parcheggiarlo né disconnetterlo, altrimenti ucciderebbe la sessione
                // appena ripristinata (era la causa del "Bye"/kick al 2° reconnect).
                if (this.habbo.getClient() == this && this.habbo.isOnline()) {
                    // Try to park the habbo in the grace period instead of immediate disconnect
                    boolean parked = allowSessionResume && SessionResumeManager.getInstance().parkHabbo(this.habbo, this.ssoTicket);

                    if (!parked) {
                        // No grace period configured — immediate disconnect as before
                        this.habbo.getHabboInfo().setOnline(false);
                        this.habbo.disconnect();
                    }
                    // If parked, do NOT call disconnect() — the habbo stays in the room
                }

                this.habbo = null;
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
