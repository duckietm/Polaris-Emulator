package polaris.messages.outgoing.wired;

import polaris.hotel.wired.leaderboard.WiredLeaderboardChunk;
import polaris.hotel.wired.leaderboard.WiredLeaderboardRow;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official variable leaderboard chunk (AIR 3979): up to 50 ranked rows of one panel and variable,
 * and the panel's total. The two times are 64-bit epoch milliseconds, written high half first.
 */
public class WiredLeaderboardChunkComposer extends MessageComposer {
    private final int overlayId;
    private final int revision;
    private final String variableId;
    private final WiredLeaderboardChunk chunk;

    public WiredLeaderboardChunkComposer(int overlayId, int revision, String variableId, WiredLeaderboardChunk chunk) {
        this.overlayId = overlayId;
        this.revision = revision;
        this.variableId = variableId;
        this.chunk = chunk;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLeaderboardChunkComposer);
        this.response.appendInt(this.overlayId);
        this.response.appendInt(this.revision);
        this.response.appendString(this.variableId);
        this.response.appendInt(this.chunk.index());
        this.response.appendInt(this.chunk.totalCount());
        this.response.appendInt(this.chunk.rows().size());
        for (WiredLeaderboardRow row : this.chunk.rows()) {
            this.response.appendInt(row.rank());
            this.response.appendInt(row.userId());
            this.response.appendString(row.username());
            this.response.appendString(row.figure());
            this.response.appendString(row.rawValue());
            this.response.appendInt((int) (row.creationTime() >>> 32));
            this.response.appendInt((int) row.creationTime());
            this.response.appendInt((int) (row.lastUpdateTime() >>> 32));
            this.response.appendInt((int) row.lastUpdateTime());
            this.response.appendBoolean(row.level() != null);
            if (row.level() != null) {
                this.response.appendString(Integer.toString(row.level()));
            }
            this.response.appendBoolean(row.text() != null);
            if (row.text() != null) {
                this.response.appendString(row.text());
            }
        }
        return this.response;
    }
}
