package polaris.messages.outgoing.camera;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CameraRoomThumbnailSavedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CameraRoomThumbnailSavedComposer);
        return this.response;
    }
}
