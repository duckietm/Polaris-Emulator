package com.eu.habbo.messages.incoming.hotlooks;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.hotlooks.HotLook;
import com.eu.habbo.habbohotel.hotlooks.HotLooksManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotlooks.HotLooksComposer;
import java.util.ArrayList;
import java.util.List;

/** AIR 13 GetHotLooks: the avatar editor asks for up to {@code count} looks per gender. */
public class GetHotLooksEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();

        if (this.client.getHabbo() == null) {
            return;
        }

        // AIR's HotLooksModel keeps one list per gender and shows the one of the gender picked in
        // the editor, so both are sent: switching gender there shows the other list at once.
        HotLooksManager manager = Emulator.getGameEnvironment().getHotLooksManager();
        List<HotLook> looks = new ArrayList<>(manager.getHotLooks(HotLook.MALE, count));
        looks.addAll(manager.getHotLooks(HotLook.FEMALE, count));
        this.client.sendResponse(new HotLooksComposer(looks));
    }
}
