package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolIssueHandlerDimensionsComposer extends MessageComposer {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public ModToolIssueHandlerDimensionsComposer(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueHandlerDimensionsComposer);
        this.response.appendInt(this.x);
        this.response.appendInt(this.y);
        this.response.appendInt(this.width);
        this.response.appendInt(this.height);
        return this.response;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
