package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.messages.outgoing.modtool.HelperRequestDisabledComposer;
import com.eu.habbo.messages.outgoing.modtool.ReportRoomFormComposer;
import java.util.List;

/**
 * The checks the room report makes before it files a ticket, for the other report packets (forum comment, thread,
 * photo, private chat): a muted user files none, and a user with an open ticket is shown it instead. Those packets
 * used to file a ticket and alert every moderator for each one sent.
 */
final class ModToolReportGate {
    /** The least time between two reports of the same kind from one connection. */
    static final int RATE_LIMIT_MS = 2000;

    private ModToolReportGate() {}

    /** True when the report is refused; the client has then been answered. */
    static boolean refuse(GameClient client, ModToolManager modTools) {
        if (!client.getHabbo().getHabboStats().allowTalk()) {
            client.sendResponse(new HelperRequestDisabledComposer());
            return true;
        }

        List<ModToolIssue> issues = modTools.openTicketsForHabbo(client.getHabbo());
        if (!issues.isEmpty()) {
            client.sendResponse(new ReportRoomFormComposer(issues));
            return true;
        }

        return false;
    }
}
