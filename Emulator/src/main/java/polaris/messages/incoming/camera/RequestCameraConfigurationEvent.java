package polaris.messages.incoming.camera;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.camera.CameraPriceComposer;

public class RequestCameraConfigurationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new CameraPriceComposer(
                Emulator.getConfig().getInt("camera.price.credits"),
                Emulator.getConfig().getInt("camera.price.points"),
                Emulator.getConfig().getInt("camera.price.points.publish")));
    }
}
