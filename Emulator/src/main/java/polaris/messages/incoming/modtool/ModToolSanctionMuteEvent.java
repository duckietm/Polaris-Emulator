package polaris.messages.incoming.modtool;

import java.util.ArrayList;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolSanctionItem;
import polaris.hotel.modtool.ModToolSanctionLevelItem;
import polaris.hotel.modtool.ModToolSanctions;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolIssueHandledComposer;
import polaris.messages.outgoing.modtool.ModeratorActionResultComposer;

public class ModToolSanctionMuteEvent extends MessageHandler {
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
                // Without sanctions the user is muted for one hour below.
                String cautionType = "MUTE";
                int cautionHours = 1;

                if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                    Map<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap =
                            Emulator.getGameEnvironment()
                                    .getModToolSanctions()
                                    .getSanctions(user.getUserInfo().getId());
                    ArrayList<ModToolSanctionItem> modToolSanctionItems =
                            modToolSanctionItemsHashMap.get(user.getUserInfo().getId());

                    if (modToolSanctionItems != null && !modToolSanctionItemsHashMap.isEmpty()) {
                        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);
                        ModToolSanctionLevelItem modToolSanctionLevelItem =
                                modToolSanctions.getSanctionLevelItem(item.sanctionLevel);

                        // The mute ends this many hours from now, as a unix timestamp in seconds.
                        // Hours used to be turned into seconds and added to milliseconds, so a
                        // one-hour mute ended after about three and a half seconds.
                        int muteDurationTimestamp = modToolSanctionLevelItem == null
                                ? 0
                                : ModToolManager.banExpiry(
                                        Emulator.getIntUnixTimestamp(),
                                        ModToolManager.hoursToSeconds(modToolSanctionLevelItem.sanctionHourLength));

                        ModToolSanctionLevelItem applied = modToolSanctions.levelAfter(item.sanctionLevel);
                        cautionType = applied == null ? "MUTE" : applied.sanctionType;
                        cautionHours = ModToolSanctions.appliedHours(
                                applied,
                                modToolSanctionLevelItem == null ? 0 : modToolSanctionLevelItem.sanctionHourLength);

                        modToolSanctions.run(
                                userId,
                                this.client.getUser(),
                                item.sanctionLevel,
                                cfhTopic,
                                message,
                                0,
                                true,
                                muteDurationTimestamp);
                    } else {
                        ModToolSanctionLevelItem applied = modToolSanctions.levelAfter(0);
                        cautionType = applied == null ? "MUTE" : applied.sanctionType;
                        cautionHours = ModToolSanctions.appliedHours(applied, 0);

                        modToolSanctions.run(userId, this.client.getUser(), 0, cfhTopic, message, 0, false, 0);
                    }
                } else {
                    user.mute(60 * 60, false);
                    user.alert(message);
                    this.client
                            .getUser()
                            .whisper(Emulator.getTexts()
                                    .getValue("commands.succes.cmd_mute.muted")
                                    .replace("%user%", user.getUserInfo().getUsername()));
                }

                ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
                ModToolManager.recordCaution(
                        userId, this.client.getUser().getUserInfo().getId(), cautionType, message, cautionHours);
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
