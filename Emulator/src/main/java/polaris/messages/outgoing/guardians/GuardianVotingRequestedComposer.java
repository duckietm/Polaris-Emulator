package polaris.messages.outgoing.guardians;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Calendar;
import polaris.hotel.guides.GuardianTicket;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuardianVotingRequestedComposer extends MessageComposer {
    private final GuardianTicket ticket;

    public GuardianVotingRequestedComposer(GuardianTicket ticket) {
        this.ticket = ticket;
    }

    @Override
    protected ServerMessage composeInternal() {
        Int2IntMap mappedUsers = new Int2IntOpenHashMap();
        mappedUsers.put(this.ticket.getReported().getUserInfo().getId(), 0);

        Calendar c = Calendar.getInstance();
        c.setTime(this.ticket.getDate());

        // GuideSessionController.setStateGuardianChatReviewVote needs six numbers before the ";"
        // (year month day hour minute second) and rebuilds the incident time as
        // new Date(y, month - 1, d, h, min, s); with only five it falls back to "now" and the
        // "(x ago)" label is always zero. Calendar.MONTH is 0-based, the client subtracts one.
        StringBuilder fullMessage = new StringBuilder(c.get(Calendar.YEAR) + " ");
        fullMessage.append(c.get(Calendar.MONTH) + 1).append(" ");
        fullMessage.append(c.get(Calendar.DAY_OF_MONTH)).append(" ");
        fullMessage.append(c.get(Calendar.HOUR_OF_DAY)).append(" ");
        fullMessage.append(c.get(Calendar.MINUTE)).append(" ");
        fullMessage.append(c.get(Calendar.SECOND)).append(";");

        fullMessage.append("\r");

        for (ModToolChatLog chatLog : this.ticket.getChatLogs()) {
            if (!mappedUsers.containsKey(chatLog.userId)) {
                mappedUsers.put(chatLog.userId, mappedUsers.size());
            }

            fullMessage
                    .append("unused;")
                    .append(mappedUsers.get(chatLog.userId))
                    .append(";")
                    .append(chatLog.message)
                    .append("\r");
        }

        this.response.init(Outgoing.GuardianVotingRequestedComposer);
        this.response.appendInt(this.ticket.getTimeLeft());
        this.response.appendString(fullMessage.toString());

        // 2015 10 17 14 24 30
        return this.response;
    }

    public GuardianTicket getTicket() {
        return ticket;
    }
}
