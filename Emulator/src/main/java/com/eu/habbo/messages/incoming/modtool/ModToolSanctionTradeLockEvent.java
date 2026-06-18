package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandledComposer;
import gnu.trove.map.hash.THashMap;

import java.util.ArrayList;

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

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolTicketGuard.isPositiveId(cfhTopic) || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

            if (habbo != null) {
                if (!ModToolManager.canModerateTarget(this.client.getHabbo(), userId)) {
                    return;
                }

                ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();

                if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                    THashMap<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap = Emulator.getGameEnvironment().getModToolSanctions().getSanctions(userId);
                    ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap.get(userId);

                    if (modToolSanctionItems != null && !modToolSanctionItems.isEmpty()) {
                        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                        if (item.probationTimestamp > 0 && item.probationTimestamp >= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.run(userId, this.client.getHabbo(), item.sanctionLevel, cfhTopic, message, duration, false, 0);
                        } else {
                            modToolSanctions.run(userId, this.client.getHabbo(), item.sanctionLevel, cfhTopic, message, duration, false, 0);
                        }
                    } else {
                        modToolSanctions.run(userId, this.client.getHabbo(), 0, cfhTopic, message, duration, false, 0);
                    }
                } else {
                    habbo.getHabboStats().setAllowTrade(false);
                    habbo.alert(message);
                }

                ModToolManager.bumpUserSettingCounter(userId, "tradelock_amount");
            } else {
                this.client.sendResponse(new ModToolIssueHandledComposer(Emulator.getTexts().getValue("generic.user.not_found").replace("%user%", Emulator.getConfig().getValue("hotel.player.name"))));
            }
        }
    }
}
