package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolCategory;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ModToolComposer extends MessageComposer {
    private final Habbo habbo;

    public ModToolComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolComposer);

        if (this.habbo.hasPermission(Permission.ACC_MODTOOL_TICKET_Q)) {
            Set<ModToolIssue> openTickets = new HashSet<>();

            Map<Integer, ModToolIssue> tickets = Emulator.getGameEnvironment().getModToolManager().getTickets();

            for (ModToolIssue t : tickets.values()) {
                if (t.state != ModToolTicketState.CLOSED)
                    openTickets.add(t);
            }

            int ticketsCount = openTickets.size();

            if (ticketsCount > 100) {
                ticketsCount = 100;
            }

            this.response.appendInt(ticketsCount); //tickets

            Iterator<ModToolIssue> it = openTickets.iterator();

            for (int i = 0; i < ticketsCount; i++) {
                it.next().serialize(this.response);
            }
        } else {
            this.response.appendInt(0);
        }

        synchronized (Emulator.getGameEnvironment().getModToolManager().getPresets()) {
            this.response.appendInt(Emulator.getGameEnvironment().getModToolManager().getPresets().get("user").size());
            for (String s : Emulator.getGameEnvironment().getModToolManager().getPresets().get("user")) {
                this.response.appendString(s);
            }
        }

        this.response.appendInt(Emulator.getGameEnvironment().getModToolManager().getCategory().size());

        for (ModToolCategory category : Emulator.getGameEnvironment().getModToolManager().getCategory().values()) {
            this.serializeCategory(category);
        }

        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_TICKET_Q)); //ticketQueueueuhuehuehuehue
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_USER_LOGS)); //user chatlogs
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_USER_ALERT)); //can send caution
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_USER_KICK)); //can send kick
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_USER_BAN)); //can send ban
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_ROOM_INFO)); //room info ??Not sure
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_MODTOOL_ROOM_LOGS)); //room chatlogs ??Not sure

        synchronized (Emulator.getGameEnvironment().getModToolManager().getPresets()) {
            this.response.appendInt(Emulator.getGameEnvironment().getModToolManager().getPresets().get("room").size());
            for (String s : Emulator.getGameEnvironment().getModToolManager().getPresets().get("room")) {
                this.response.appendString(s);
            }
        }

        return this.response;
    }

    private void serializeCategory(ModToolCategory category) {
        this.response.appendString(category.getName());


//

//

    }

    public Habbo getHabbo() {
        return habbo;
    }
}
