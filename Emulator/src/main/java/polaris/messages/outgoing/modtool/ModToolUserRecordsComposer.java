package polaris.messages.outgoing.modtool;

import java.util.List;
import polaris.hotel.modtool.ModToolUserRecords;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The records behind one count of the mod tool's user info ({@code ModToolRequestUserRecordsEvent}):
 * {@code int userId, int kind, int undetailed, int count}, then per row {@code int timestamp, string username,
 * string staffName, string type, string reason, int expires}.
 */
public class ModToolUserRecordsComposer extends MessageComposer {
    private final int userId;
    private final int kind;
    private final int undetailed;
    private final List<ModToolUserRecords.Entry> entries;

    public ModToolUserRecordsComposer(int userId, int kind, int undetailed, List<ModToolUserRecords.Entry> entries) {
        this.userId = userId;
        this.kind = kind;
        this.undetailed = undetailed;
        this.entries = entries == null ? List.of() : entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolUserRecordsComposer);
        this.response.appendInt(this.userId);
        this.response.appendInt(this.kind);
        this.response.appendInt(this.undetailed);
        this.response.appendInt(this.entries.size());

        for (ModToolUserRecords.Entry entry : this.entries) {
            this.response.appendInt(entry.timestamp());
            this.response.appendString(text(entry.username()));
            this.response.appendString(text(entry.staffName()));
            this.response.appendString(text(entry.type()));
            this.response.appendString(text(entry.reason()));
            this.response.appendInt(entry.expires());
        }

        return this.response;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
