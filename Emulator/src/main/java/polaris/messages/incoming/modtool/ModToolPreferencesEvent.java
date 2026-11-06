package polaris.messages.incoming.modtool;

import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;

/**
 * Official {@code ModToolPreferencesComposer} (31): the moderator moved or
 * resized the issue handler window. The geometry is stored per account and
 * played back with {@code ModToolIssueHandlerDimensionsComposer} (1576).
 */
public class ModToolPreferencesEvent extends MessageHandler {
    static final int MAX_WINDOW_COORDINATE = 10000;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            return;
        }

        int x = this.packet.readInt();
        int y = this.packet.readInt();
        int width = this.packet.readInt();
        int height = this.packet.readInt();

        if (!isSaneCoordinate(x) || !isSaneCoordinate(y) || !isSaneSize(width) || !isSaneSize(height)) {
            return;
        }

        this.client.getUser().getUserStats().setModToolWindow(x, y, width, height);
    }

    static boolean isSaneCoordinate(int value) {
        return value >= -MAX_WINDOW_COORDINATE && value <= MAX_WINDOW_COORDINATE;
    }

    static boolean isSaneSize(int value) {
        return value >= 0 && value <= MAX_WINDOW_COORDINATE;
    }
}
