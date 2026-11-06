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

public class ModToolSanctionAlertEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());
        int cfhTopic = this.packet.readInt();

        if (!ModToolTicketGuard.isPositiveId(userId)
                || !ModToolTicketGuard.isPositiveId(cfhTopic)
                || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

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
                            Emulator.getGameEnvironment()
                                    .getModToolSanctions()
                                    .getSanctions(user.getUserInfo().getId());
                    ArrayList<ModToolSanctionItem> modToolSanctionItems =
                            modToolSanctionItemsHashMap.get(user.getUserInfo().getId());

                    if (modToolSanctionItems != null && !modToolSanctionItems.isEmpty()) {
                        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                        if (item != null
                                && item.probationTimestamp > 0
                                && item.probationTimestamp >= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.run(
                                    userId, this.client.getUser(), item.sanctionLevel, cfhTopic, message, 0, false, 0);
                        } else {
                            modToolSanctions.run(
                                    userId, this.client.getUser(), item.sanctionLevel, cfhTopic, message, 0, false, 0);
                        }
                    } else {
                        modToolSanctions.run(userId, this.client.getUser(), 0, cfhTopic, message, 0, false, 0);
                    }
                } else {
                    user.alert(message);
                }

                ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
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
