package com.eu.habbo.messages.outgoing.guardians;

import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.Calendar;

public class GuardianVotingRequestedComposer extends MessageComposer {
    private final GuardianTicket ticket;

    public GuardianVotingRequestedComposer(GuardianTicket ticket) {
        this.ticket = ticket;
    }

    @Override
    protected ServerMessage composeInternal() {
        Int2IntMap mappedUsers = new Int2IntOpenHashMap();
        mappedUsers.put(this.ticket.getReported().getHabboInfo().getId(), 0);

        Calendar c = Calendar.getInstance();
        c.setTime(this.ticket.getDate());

        StringBuilder fullMessage = new StringBuilder(c.get(Calendar.YEAR) + " ");
        fullMessage.append(c.get(Calendar.MONTH)).append(" ");
        fullMessage.append(c.get(Calendar.DAY_OF_MONTH)).append(" ");
        fullMessage.append(c.get(Calendar.MINUTE)).append(" ");
        fullMessage.append(c.get(Calendar.SECOND)).append(";");

        fullMessage.append("\r");

        for (ModToolChatLog chatLog : this.ticket.getChatLogs()) {
            if (!mappedUsers.containsKey(chatLog.habboId)) {
                mappedUsers.put(chatLog.habboId, mappedUsers.size());
            }

            fullMessage.append("unused;").append(mappedUsers.get(chatLog.habboId)).append(";").append(chatLog.message).append("\r");
        }

        this.response.init(Outgoing.GuardianVotingRequestedComposer);
        this.response.appendInt(this.ticket.getTimeLeft());
        this.response.appendString(fullMessage.toString());

        //2015 10 17 14 24 30
        return this.response;
    }

    public GuardianTicket getTicket() {
        return ticket;
    }
}
