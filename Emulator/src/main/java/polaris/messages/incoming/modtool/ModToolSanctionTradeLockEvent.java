package polaris.messages.incoming.modtool;

import java.util.ArrayList;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolSanctionItem;
import polaris.hotel.modtool.ModToolSanctions;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolIssueHandledComposer;
import polaris.messages.outgoing.modtool.ModeratorActionResultComposer;

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

        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

            if (user != null) {
                if (!ModToolManager.canModerateTarget(this.client.getUser(), userId)) {
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
                            this.client.getUser(),
                            sanctionLevel,
                            cfhTopic,
                            message,
                            tradeLockedUntil,
                            false,
                            0);
                } else {
                    user.alert(message);
                }

                // The sanction row alone does not stop the session that is already
                // trading: only the next login reads trade_locked_until.
                user.getUserStats().setAllowTrade(false);

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
