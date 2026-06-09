package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionLevelItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.map.hash.THashMap;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

public class ModToolSanctionInfoComposer extends MessageComposer {

    private final Habbo habbo;

    public ModToolSanctionInfoComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();

        Date probationEndTime;
        Date probationStartTime;

        if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
            THashMap<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap = Emulator.getGameEnvironment().getModToolSanctions().getSanctions(habbo.getHabboInfo().getId());
            ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap.get(habbo.getHabboInfo().getId());

            if (modToolSanctionItems != null && modToolSanctionItems.size() > 0) {
                ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                ModToolSanctionItem prevItem = null;
                if (modToolSanctionItems.size() > 1 && modToolSanctionItems.get(modToolSanctionItems.size() - 2) != null) {
                    prevItem = modToolSanctionItems.get(modToolSanctionItems.size() - 2);
                }

                ModToolSanctionLevelItem modToolSanctionLevelItem = modToolSanctions.getSanctionLevelItem(item.sanctionLevel);
                ModToolSanctionLevelItem nextModToolSanctionLevelItem = modToolSanctions.getSanctionLevelItem(item.sanctionLevel + 1);

                if (item.probationTimestamp > 0) {
                    probationEndTime = new Date((long) item.probationTimestamp * 1000);

                    probationStartTime = Date.from(probationEndTime.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .minusDays(modToolSanctions.getProbationDays(modToolSanctionLevelItem))
                            .toInstant());

                    Date tradeLockedUntil = null;

                    if (item.tradeLockedUntil > 0) {
                        tradeLockedUntil = new Date((long) item.tradeLockedUntil * 1000);
                    }

                    this.response.init(Outgoing.ModToolSanctionInfoComposer);

                    this.response.appendBoolean(prevItem != null && prevItem.probationTimestamp > 0); // has prev sanction
                    this.response.appendBoolean(item.probationTimestamp >= Emulator.getIntUnixTimestamp()); // is on probation
                    this.response.appendString(modToolSanctions.getSanctionType(modToolSanctionLevelItem)); // current sanction type
                    this.response.appendInt(modToolSanctions.getTimeOfSanction(modToolSanctionLevelItem)); // time of current sanction
                    this.response.appendInt(30); // unused - reserved protocol value
                    this.response.appendString(item.reason.equals("") ? "cfh.reason.EMPTY" : item.reason); // reason
                    this.response.appendString(probationStartTime == null ? Emulator.getDate().toString() : probationStartTime.toString()); // probation start time
                    this.response.appendInt(0); // unused - reserved protocol value
                    this.response.appendString(modToolSanctions.getSanctionType(nextModToolSanctionLevelItem)); // next sanction type
                    this.response.appendInt(modToolSanctions.getTimeOfSanction(nextModToolSanctionLevelItem)); // time to be applied in next sanction (in hours)
                    this.response.appendInt(30); // unused - reserved protocol value
                    this.response.appendBoolean(item.isMuted); // muted
                    this.response.appendString(tradeLockedUntil == null ? "" : tradeLockedUntil.toString()); // trade locked until
                } else {
                    return cleanResponse();
                }

            } else {
                return cleanResponse();
            }
        }

        return this.response;
    }

    private ServerMessage cleanResponse() {
        this.response.init(Outgoing.ModToolSanctionInfoComposer);

        this.response.appendBoolean(false); // has prev sanction
        this.response.appendBoolean(false); // is on probation
        this.response.appendString("ALERT"); // last sanction type
        this.response.appendInt(0); // time of current sanction
        this.response.appendInt(30); // unused - reserved protocol value
        this.response.appendString("cfh.reason.EMPTY"); // reason
        this.response.appendString(Emulator.getDate().toString()); // probation start time
        this.response.appendInt(0); // unused - reserved protocol value
        this.response.appendString("ALERT"); // next sanction type
        this.response.appendInt(0); // time to be applied in next sanction (in hours)
        this.response.appendInt(30); // unused - reserved protocol value
        this.response.appendBoolean(false); // muted
        this.response.appendString(""); // trade locked until

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}