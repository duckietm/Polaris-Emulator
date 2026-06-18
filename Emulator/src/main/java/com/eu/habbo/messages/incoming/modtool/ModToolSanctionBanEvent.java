package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import gnu.trove.map.hash.THashMap;

import java.util.ArrayList;

public class ModToolSanctionBanEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    public static final int BAN_18_HOURS = 3;
    public static final int BAN_7_DAYS = 4;
    public static final int BAN_30_DAYS_STEP_1 = 5;
    public static final int BAN_30_DAYS_STEP_2 = 7;
    public static final int BAN_100_YEARS = 6;
    public static final int BAN_AVATAR_ONLY_100_YEARS = 106;

    public final int DAY_IN_SECONDS = 24 * 60 * 60;

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());
        int cfhTopic = this.packet.readInt();
        int banType = this.packet.readInt();
        this.packet.readBoolean();

        int duration = 0;

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolTicketGuard.isPositiveId(cfhTopic) || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

        switch (banType) {
            case BAN_18_HOURS:
                duration = 18 * 60 * 60;
                break;
            case BAN_7_DAYS:
                duration = 7 * this.DAY_IN_SECONDS;
                break;
            case BAN_30_DAYS_STEP_1:
            case BAN_30_DAYS_STEP_2:
                duration = 30 * this.DAY_IN_SECONDS;
                break;
            case BAN_100_YEARS:
            case BAN_AVATAR_ONLY_100_YEARS:
                duration = Emulator.getIntUnixTimestamp();
        }
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();

            if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                THashMap<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap = Emulator.getGameEnvironment().getModToolSanctions().getSanctions(userId);
                ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap.get(userId);

                if (modToolSanctionItems != null && !modToolSanctionItemsHashMap.isEmpty()) {
                    ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                    if (item.probationTimestamp > 0 && item.probationTimestamp >= Emulator.getIntUnixTimestamp()) {
                        modToolSanctions.run(userId, this.client.getHabbo(), item.sanctionLevel, cfhTopic, message, 0, false, 0);
                    } else {
                        modToolSanctions.run(userId, this.client.getHabbo(), item.sanctionLevel, cfhTopic, message, 0, false, 0);
                    }
                } else {
                    modToolSanctions.run(userId, this.client.getHabbo(), 0, cfhTopic, message, 0, false, 0);
                }
            } else {
                Emulator.getGameEnvironment().getModToolManager().ban(userId, this.client.getHabbo(), message, duration, ModToolBanType.ACCOUNT, cfhTopic);
            }

            ModToolManager.bumpUserSettingCounter(userId, "cfh_bans");
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.ban").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
