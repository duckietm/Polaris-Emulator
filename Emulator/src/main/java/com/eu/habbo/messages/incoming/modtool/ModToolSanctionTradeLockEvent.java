package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandledComposer;
import com.eu.habbo.messages.outgoing.modtool.ModeratorActionResultComposer;
import java.util.ArrayList;
import java.util.Map;

public class ModToolSanctionTradeLockEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());
        int duration = this.packet.readInt();
        int cfhTopic = this.packet.readInt();

        if (!ModToolTicketGuard.isPositiveId(userId)
                || !ModToolTicketGuard.isPositiveId(cfhTopic)
                || !ModToolInputGuard.isSafeMessage(message)
                || duration <= 0) {
            return;
        }

        // The packet carries a length in seconds; sanctions.trade_locked_until holds an
        // absolute timestamp. Storing the raw number put every lock in 1970, so the next
        // login saw it as expired and unlocked the user again - "permanent" included.
        int tradeLockedUntil = ModToolManager.banExpiry(Emulator.getIntUnixTimestamp(), duration);

        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

            if (habbo != null) {
                if (!ModToolManager.canModerateTarget(this.client.getHabbo(), userId)) {
                    return;
                }

                ModToolSanctions modToolSanctions =
                        Emulator.getGameEnvironment().getModToolSanctions();

                if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                    Map<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap =
                            Emulator.getGameEnvironment().getModToolSanctions().getSanctions(userId);
                    ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap.get(userId);

                    int sanctionLevel = 0;
                    if (modToolSanctionItems != null && !modToolSanctionItems.isEmpty()) {
                        sanctionLevel = modToolSanctionItems.get(modToolSanctionItems.size() - 1).sanctionLevel;
                    }

                    modToolSanctions.run(
                            userId,
                            this.client.getHabbo(),
                            sanctionLevel,
                            cfhTopic,
                            message,
                            tradeLockedUntil,
                            false,
                            0);
                } else {
                    habbo.alert(message);
                }

                // The sanction row alone does not stop the session that is already
                // trading: only the next login reads trade_locked_until.
                habbo.getHabboStats().setAllowTrade(false);

                ModToolManager.bumpUserSettingCounter(userId, "tradelock_amount");
                this.client.sendResponse(new ModeratorActionResultComposer(userId, true));
            } else {
                this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
                this.client.sendResponse(new ModToolIssueHandledComposer(Emulator.getTexts()
                        .getValue("generic.user.not_found")
                        .replace("%user%", Emulator.getConfig().getValue("hotel.player.name"))));
            }
        }
    }
}
