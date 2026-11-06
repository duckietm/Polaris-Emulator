package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideToolsComposer;

public class RequestGuideToolEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean onDuty = this.packet.readBoolean();

        if (onDuty) {
            this.packet.readBoolean();
            boolean helperRequests = this.packet.readBoolean();
            boolean bullyReports = this.packet.readBoolean();

            if (!this.client.getUser().hasPermission(Permission.ACC_HELPER_USE_GUIDE_TOOL)) return;

            if (helperRequests && !this.client.getUser().hasPermission(Permission.ACC_HELPER_GIVE_GUIDE_TOURS))
                helperRequests = false;

            if (bullyReports && !this.client.getUser().hasPermission(Permission.ACC_HELPER_JUDGE_CHAT_REVIEWS))
                bullyReports = false;

            if (helperRequests) {
                Emulator.getGameEnvironment().getGuideManager().setOnGuide(this.client.getUser(), onDuty);
            }

            if (bullyReports) {
                Emulator.getGameEnvironment().getGuideManager().setOnGuardian(this.client.getUser(), onDuty);
            }

            this.client.sendResponse(new GuideToolsComposer(onDuty));
        } else {
            Emulator.getGameEnvironment().getGuideManager().setOnGuide(this.client.getUser(), onDuty);
            Emulator.getGameEnvironment().getGuideManager().setOnGuardian(this.client.getUser(), onDuty);
            this.client.sendResponse(new GuideToolsComposer(onDuty));
        }
    }
}
