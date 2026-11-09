package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolUserRecords;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolUserRecordsComposer;

/**
 * The mod tool's user info asks for the records behind one of its counts: {@code int userId, int kind}, kind
 * 0 cautions, 1 the user's bans, 2 the bans of accounts on the user's machine, 3 trade locks.
 */
public class ModToolRequestUserRecordsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.userinfo")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
            return;
        }

        int userId = this.packet.readInt();
        int kind = this.packet.readInt();

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolUserRecords.isKind(kind)) {
            return;
        }

        ModToolUserRecords.Records records = ModToolUserRecords.load(userId, kind);

        this.client.sendResponse(new ModToolUserRecordsComposer(userId, kind, records.undetailed(), records.entries()));
    }
}
