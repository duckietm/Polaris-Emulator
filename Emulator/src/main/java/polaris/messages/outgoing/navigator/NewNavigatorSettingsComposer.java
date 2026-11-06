package polaris.messages.outgoing.navigator;

import polaris.hotel.users.UserNavigatorWindowSettings;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorSettingsComposer extends MessageComposer {
    private final UserNavigatorWindowSettings windowSettings;

    public NewNavigatorSettingsComposer(UserNavigatorWindowSettings windowSettings) {
        this.windowSettings = windowSettings;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorSettingsComposer);
        this.response.appendInt(this.windowSettings.x);
        this.response.appendInt(this.windowSettings.y);
        this.response.appendInt(this.windowSettings.width);
        this.response.appendInt(this.windowSettings.height);
        this.response.appendBoolean(this.windowSettings.openSearches);
        this.response.appendInt(0);
        return this.response;
    }

    public UserNavigatorWindowSettings getWindowSettings() {
        return windowSettings;
    }
}
