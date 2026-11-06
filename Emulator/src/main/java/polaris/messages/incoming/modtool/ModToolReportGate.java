package polaris.messages.incoming.modtool;

import java.util.List;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.messages.outgoing.modtool.HelperRequestDisabledComposer;
import polaris.messages.outgoing.modtool.ReportRoomFormComposer;

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
        if (!client.getUser().getUserStats().allowTalk()) {
            client.sendResponse(new HelperRequestDisabledComposer());
            return true;
        }

        List<ModToolIssue> issues = modTools.openTicketsForUser(client.getUser());
        if (!issues.isEmpty()) {
            client.sendResponse(new ReportRoomFormComposer(issues));
            return true;
        }

        return false;
    }
}
