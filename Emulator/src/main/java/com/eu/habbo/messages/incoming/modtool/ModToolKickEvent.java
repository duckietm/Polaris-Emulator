package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModeratorActionResultComposer;

public class ModToolKickEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.kick")
                            .replace(
                                    "%username%",
                                    this.client.getHabbo().getHabboInfo().getUsername()));
            return;
        }

        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());
        this.packet.readInt(); // Moderator category; kick auditing currently stores the normalized message.

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        Emulator.getGameEnvironment().getModToolManager().kick(this.client.getHabbo(), target, message);
        this.client.sendResponse(new ModeratorActionResultComposer(userId, target != null));
    }
}
