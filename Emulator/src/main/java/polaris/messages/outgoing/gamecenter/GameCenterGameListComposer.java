package polaris.messages.outgoing.gamecenter;

import polaris.Emulator;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GameCenterGameListComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GameCenterGameListComposer);
        this.response.appendInt(2); // Count

        this.response.appendInt(0);
        this.response.appendString("snowwar");
        this.response.appendString("93d4f3");
        this.response.appendString("");
        this.response.appendString(Emulator.getConfig().getValue("images.gamecenter.snowwar"));
        this.response.appendString("");

        this.response.appendInt(3);
        this.response.appendString("basejump");
        this.response.appendString("68bbd2"); // Background Color
        this.response.appendString(""); // Text color
        this.response.appendString(Emulator.getConfig().getValue("images.gamecenter.basejump"));
        this.response.appendString("");

        this.response.appendInt(4);
        this.response.appendString("slotcar");
        this.response.appendString("4a95df");
        this.response.appendString("");
        this.response.appendString("http://habboo-a.akamaihd.net/gamecenter/Sulake/slotcar/20130214010101/");
        this.response.appendString("");

        return this.response;
    }
}
