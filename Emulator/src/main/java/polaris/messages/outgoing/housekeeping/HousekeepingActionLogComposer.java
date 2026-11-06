package polaris.messages.outgoing.housekeeping;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HousekeepingActionLogComposer extends MessageComposer {
    public static class Row {
        public final int id;
        public final int timestamp;
        public final int actorId;
        public final String actorName;
        public final String targetType;
        public final int targetId;
        public final String targetLabel;
        public final String action;
        public final String detail;
        public final boolean success;

        public Row(
                int id,
                int timestamp,
                int actorId,
                String actorName,
                String targetType,
                int targetId,
                String targetLabel,
                String action,
                String detail,
                boolean success) {
            this.id = id;
            this.timestamp = timestamp;
            this.actorId = actorId;
            this.actorName = actorName != null ? actorName : "";
            this.targetType = targetType != null ? targetType : "user";
            this.targetId = targetId;
            this.targetLabel = targetLabel != null ? targetLabel : "";
            this.action = action != null ? action : "";
            this.detail = detail != null ? detail : "";
            this.success = success;
        }
    }

    private final List<Row> rows;

    public HousekeepingActionLogComposer(List<Row> rows) {
        this.rows = rows;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingActionLogComposer);
        this.response.appendInt(this.rows != null ? this.rows.size() : 0);

        if (this.rows != null) {
            for (Row r : this.rows) {
                this.response.appendInt(r.id);
                this.response.appendInt(r.timestamp);
                this.response.appendInt(r.actorId);
                this.response.appendString(r.actorName);
                this.response.appendString(r.targetType);
                this.response.appendInt(r.targetId);
                this.response.appendString(r.targetLabel);
                this.response.appendString(r.action);
                this.response.appendString(r.detail);
                this.response.appendBoolean(r.success);
            }
        }

        return this.response;
    }
}
