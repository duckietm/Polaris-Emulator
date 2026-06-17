package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.NoAuthMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoAuthMessage
public class MachineIDEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineIDEvent.class);

    private static final int HASH_LENGTH = 64;

    @Override
    public void handle() throws Exception {
        String storedMachineId = this.packet.readString();
        this.packet.readString();
        this.packet.readString();

        if (storedMachineId.length() > HASH_LENGTH) {
            storedMachineId = storedMachineId.substring(0, HASH_LENGTH);
        }

        this.client.setMachineId(storedMachineId);

        // Persist the machine fingerprint onto the user so machine/super bans can
        // target it (createOfflineUserBan copies users.machine_id). The Nitro client
        // sends this UniqueID packet right after the SSO ticket, so the Habbo is
        // normally already loaded by the time we get here.
        if (!storedMachineId.isEmpty() && this.client.getHabbo() != null && this.client.getHabbo().getHabboInfo() != null) {
            this.client.getHabbo().getHabboInfo().setMachineID(storedMachineId);
            Emulator.getThreading().run(this.client.getHabbo());

            // The fingerprint can arrive AFTER login (UniqueID is sent right after the
            // SSO ticket), so Habbo.connect() may have skipped the MAC-ban check for
            // lack of a machineId. Enforce it now that the fingerprint is known.
            if (Emulator.getGameEnvironment().getModToolManager().hasMACBan(this.client)) {
                Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
            }
        }

        LOGGER.debug("Setting client MachineId to {}", storedMachineId);
    }
}
